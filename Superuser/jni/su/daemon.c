/*
** Copyright 2010, Adam Shanks (@ChainsDD)
** Copyright 2008, Zinx Verituse (@zinxv)
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <sys/select.h>
#include <sys/time.h>
#include <unistd.h>
#include <limits.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <getopt.h>
#include <stdint.h>
#include <pwd.h>
#include <sys/stat.h>
#include <stdarg.h>
#include <sys/types.h>
#include <pthread.h>
#include <signal.h>
#include <string.h>

#include "su.h"
#include "utils.h"
#include "pts.h"

int is_daemon = 0;
int daemon_from_uid = 0;
int daemon_from_pid = 0;

// Constants for the atty bitfield
#define ATTY_IN     1
#define ATTY_OUT    2
#define ATTY_ERR    4

static int read_int(int fd) {
    int val;
    int len = read(fd, &val, sizeof(int));
    if (len != sizeof(int)) {
        LOGE("unable to read int");
        exit(-1);
    }
    return val;
}

static void write_int(int fd, int val) {
    int written = write(fd, &val, sizeof(int));
    if (written != sizeof(int)) {
        PLOGE("unable to write int");
        exit(-1);
    }
}

static char* read_string(int fd) {
    int len = read_int(fd);
    if (len > PATH_MAX || len < 0) {
        LOGE("invalid string length %d", len);
        exit(-1);
    }
    char* val = malloc(sizeof(char) * (len + 1));
    if (val == NULL) {
        LOGE("unable to malloc string");
        exit(-1);
    }
    val[len] = '\0';
    int amount = read(fd, val, len);
    if (amount != len) {
        LOGE("unable to read string");
        exit(-1);
    }
    return val;
}

static void write_string(int fd, char* val) {
    int len = strlen(val);
    write_int(fd, len);
    int written = write(fd, val, len);
    if (written != len) {
        PLOGE("unable to write string");
        exit(-1);
    }
}

static int run_daemon_child(int infd, int outfd, int errfd, int argc, char** argv) {
    if (-1 == dup2(outfd, STDOUT_FILENO)) {
        PLOGE("dup2 child outfd");
        exit(-1);
    }

    if (-1 == dup2(errfd, STDERR_FILENO)) {
        PLOGE("dup2 child errfd");
        exit(-1);
    }

    if (-1 == dup2(infd, STDIN_FILENO)) {
        PLOGE("dup2 child infd");
        exit(-1);
    }

    close(infd);
    close(outfd);
    close(errfd);

    return main(argc, argv);
}

// Ensures all the data is written out
static int write_blocking(int fd, char *buf, size_t bufsz) {
    ssize_t ret, written;

    written = 0;
    do {
        ret = write(fd, buf + written, bufsz - written);
        if (ret == -1) return -1;
        written += ret;
    } while (written < bufsz);

    return 0;
}

/**
 * Pump data from input FD to output FD. If close_output is
 * true, then close the output FD when we're done.
 */
static void pump_ex(int input, int output, int close_output) {
    char buf[4096];
    int len;
    while ((len = read(input, buf, 4096)) > 0) {
        if (write_blocking(output, buf, len) == -1) break;
    }
    close(input);
    if (close_output) close(output);
}

/**
 * Pump data from input FD to output FD. Will close the
 * output FD when done.
 */
static void pump(int input, int output) {
    pump_ex(input, output, 1);
}

static void* pump_thread(void* data) {
    int* files = (int*)data;
    int input = files[0];
    int output = files[1];
    pump(input, output);
    free(data);
    return NULL;
}

static void pump_async(int input, int output) {
    pthread_t writer;
    int* files = (int*)malloc(sizeof(int) * 2);
    if (files == NULL) {
        LOGE("unable to pump_async");
        exit(-1);
    }
    files[0] = input;
    files[1] = output;
    pthread_create(&writer, NULL, pump_thread, files);
}

// Open a FIFO and set the right permissions on it
// Terminates the app if it fails
static void create_fifo(const char *path, pid_t pidd, int uid) {
    unlink(path);
    if (mkfifo(path, 0660) != 0) {
        PLOGE("create_fifo %s", path);
        exit (-1);
    }
    chown(path, uid, 0);
    chmod(path, 0660);
}

static int daemon_accept(int fd) {
    is_daemon = 1;
    int pid = read_int(fd);
    LOGD("remote pid: %d", pid);
    int atty = read_int(fd);
    LOGD("remote atty: %d", atty);
    char *pts_slave = read_string(fd);
    LOGD("remote pts_slave: %s", pts_slave);
    daemon_from_uid = read_int(fd);
    LOGD("remote uid: %d", daemon_from_uid);
    daemon_from_pid = read_int(fd);
    LOGD("remote req pid: %d", daemon_from_pid);

    struct ucred credentials;
    int ucred_length = sizeof(struct ucred);
    /* fill in the user data structure */
    if(getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &credentials, &ucred_length)) {
        LOGE("could obtain credentials from unix domain socket");
        exit(-1);
    }
    // if the credentials on the other side of the wire are NOT root,
    // we can't trust anything being sent.
    if (credentials.uid != 0) {
        daemon_from_uid = credentials.uid;
        pid = credentials.pid;
        daemon_from_pid = credentials.pid;
    }

    int argc = read_int(fd);
    if (argc < 0 || argc > 512) {
        LOGE("unable to allocate args: %d", argc);
        exit(-1);
    }
    LOGD("remote args: %d", argc);
    char** argv = (char**)malloc(sizeof(char*) * (argc + 1));
    argv[argc] = NULL;
    int i;
    for (i = 0; i < argc; i++) {
        argv[i] = read_string(fd);
    }

    char errfile[PATH_MAX];
    char outfile[PATH_MAX];
    char infile[PATH_MAX];
    int infd, outfd, errfd;
    sprintf(outfile, "%s/%d.stdout", REQUESTOR_DAEMON_PATH, pid);
    sprintf(errfile, "%s/%d.stderr", REQUESTOR_DAEMON_PATH, pid);
    sprintf(infile, "%s/%d.stdin", REQUESTOR_DAEMON_PATH, pid);

    // Create the FIFOs if necessary
    if (!(atty & ATTY_IN)) {
        create_fifo(infile, pid, daemon_from_uid);
    }
    if (!(atty & ATTY_OUT)) {
        create_fifo(outfile, pid, daemon_from_uid);
    }
    if (!(atty & ATTY_ERR)) {
        create_fifo(errfile, pid, daemon_from_uid);
    }

    // ack
    write_int(fd, 1);

    // Fork the child process. The fork has to happen before calling
    // setsid() and opening the pseudo-terminal so that the parent
    // is not affected
    int child = fork();
    if (child < 0) {
        // fork failed, send a return code and bail out
        PLOGE("unable to fork");
        write(fd, &child, sizeof(int));
        close(fd);
        return child;
    }

    if (child != 0) {
        // In parent, wait for the child to exit, and send the exit code
        // across the wire.
        int status, code;

        free(pts_slave);

        LOGD("waiting for child exit");
        if (waitpid(child, &status, 0) > 0) {
            code = WEXITSTATUS(status);
        }
        else {
            code = -1;
        }

        // Pass the return code back to the client
        if (write(fd, &code, sizeof(int)) != sizeof(int)) {
            PLOGE("unable to write exit code");
        }

        close(fd);
        LOGD("child exited");
        return code;
    }

    // We are in the child now
    // Close the unix socket file descriptor
    close (fd);

    // Become session leader
    if (setsid() == (pid_t) -1) {
        PLOGE("setsid");
    }

    int ptsfd;
    if (atty) {
        // Opening the TTY has to occur after the
        // fork() and setsid() so that it becomes
        // our controlling TTY and not the daemon's
        ptsfd = open(pts_slave, O_RDWR);
        if (infd == -1) {
            PLOGE("open(pts_slave) daemon");
            exit(-1);
        }
    }
    free(pts_slave);

    // Get the FD for stdout
    if (atty & ATTY_OUT) {
        outfd = ptsfd;
    } else {
        outfd = open(outfile, O_WRONLY);
        if (outfd < 0) {
            PLOGE("outfd daemon %s", outfile);
            exit(-1);
        }
    }

    // ...and now stderr
    if (atty & ATTY_ERR) {
        errfd = ptsfd;
    } else {
        errfd = open(errfile, O_WRONLY);
        if (errfd < 0) {
            PLOGE("errfd daemon %s", errfile);
            exit(-1);
        }
    }

    // ...and finally stdin
    if (atty & ATTY_IN) {
        infd = ptsfd;
    } else {
        infd = open(infile, O_RDONLY);
        if (infd < 0) {
            PLOGE("infd daemon %s", infile);
            exit(-1);
        }
    }

    return run_daemon_child(infd, outfd, errfd, argc, argv);
}

int run_daemon() {
    int fd;
    struct sockaddr_un sun;

    fd = socket(AF_LOCAL, SOCK_STREAM, 0);
    if (fd < 0) {
        PLOGE("socket");
        return -1;
    }
    if (fcntl(fd, F_SETFD, FD_CLOEXEC)) {
        PLOGE("fcntl FD_CLOEXEC");
        goto err;
    }

    memset(&sun, 0, sizeof(sun));
    sun.sun_family = AF_LOCAL;
    sprintf(sun.sun_path, "%s/server", REQUESTOR_DAEMON_PATH);

    /*
     * Delete the socket to protect from situations when
     * something bad occured previously and the kernel reused pid from that process.
     * Small probability, isn't it.
     */
    unlink(sun.sun_path);
    unlink(REQUESTOR_DAEMON_PATH);

    int previous_umask = umask(027);
    mkdir(REQUESTOR_DAEMON_PATH, 0777);

    if (bind(fd, (struct sockaddr*)&sun, sizeof(sun)) < 0) {
        PLOGE("daemon bind");
        goto err;
    }

    chmod(REQUESTOR_DAEMON_PATH, 0755);
    chmod(sun.sun_path, 0777);

    umask(previous_umask);

    if (listen(fd, 10) < 0) {
        PLOGE("daemon listen");
        goto err;
    }

    int client;
    while ((client = accept(fd, NULL, NULL)) > 0) {
        if (fork_zero_fucks() == 0) {
            close(fd);
            return daemon_accept(client);
        }
        else {
            close(client);
        }
    }

    LOGE("daemon exiting");
err:
    close(fd);
    return -1;
}

// List of signals which cause process termination
static int quit_signals[] = { SIGALRM, SIGHUP, SIGPIPE, SIGQUIT, SIGTERM, SIGINT, 0 };

static void sighandler(int sig) {
    restore_stdin();

    // Assume we'll only be called before death
    // See note before sigaction() in set_stdin_raw()
    //
    // Now, close all standard I/O to cause the pumps
    // to exit so we can continue and retrieve the exit
    // code
    close(STDIN_FILENO);
    close(STDOUT_FILENO);
    close(STDERR_FILENO);

    // Put back all the default handlers
    struct sigaction act;
    int i;

    memset(&act, '\0', sizeof(act));
    act.sa_handler = SIG_DFL;
    for (i = 0; quit_signals[i]; i++) {
        if (sigaction(quit_signals[i], &act, NULL) < 0) {
            PLOGE("Error removing signal handler");
            continue;
        }
    }
}

/**
 * Setup signal handlers trap signals which should result in program termination
 * so that we can restore the terminal to its normal state and retrieve the 
 * return code.
 */
static void setup_sighandlers(void) {
    struct sigaction act;
    int i;

    // Install the termination handlers
    // Note: we're assuming that none of these signal handlers are already trapped.
    // If they are, we'll need to modify this code to save the previous handler and
    // call it after we restore stdin to its previous state.
    memset(&act, '\0', sizeof(act));
    act.sa_handler = &sighandler;
    for (i = 0; quit_signals[i]; i++) {
        if (sigaction(quit_signals[i], &act, NULL) < 0) {
            PLOGE("Error installing signal handler");
            continue;
        }
    }
}

int connect_daemon(int argc, char *argv[]) {
    char errfile[PATH_MAX];
    char outfile[PATH_MAX];
    char infile[PATH_MAX];
    int uid = getuid();
    int ptmx;
    char pts_slave[PATH_MAX];

    struct sockaddr_un sun;

    // FIFO names for apps not attached to a PTY
    sprintf(outfile, "%s/%d.stdout", REQUESTOR_DAEMON_PATH, getpid());
    sprintf(errfile, "%s/%d.stderr", REQUESTOR_DAEMON_PATH, getpid());
    sprintf(infile, "%s/%d.stdin", REQUESTOR_DAEMON_PATH, getpid());

    // Open a socket to the daemon
    int socketfd = socket(AF_LOCAL, SOCK_STREAM, 0);
    if (socketfd < 0) {
        PLOGE("socket");
        exit(-1);
    }
    if (fcntl(socketfd, F_SETFD, FD_CLOEXEC)) {
        PLOGE("fcntl FD_CLOEXEC");
        exit(-1);
    }

    memset(&sun, 0, sizeof(sun));
    sun.sun_family = AF_LOCAL;
    sprintf(sun.sun_path, "%s/server", REQUESTOR_DAEMON_PATH);

    if (0 != connect(socketfd, (struct sockaddr*)&sun, sizeof(sun))) {
        PLOGE("connect");
        exit(-1);
    }
    LOGD("connecting client %d", getpid());

    // Send some info to the daemon, starting with our PID
    write_int(socketfd, getpid());

    // Determine which one of our streams are attached to a TTY
    int atty = 0;

    if (isatty(STDIN_FILENO))  atty |= ATTY_IN;
    if (isatty(STDOUT_FILENO)) atty |= ATTY_OUT;
    if (isatty(STDERR_FILENO)) atty |= ATTY_ERR;

    if (atty) {
        // We need a PTY. Get one.
        ptmx = pts_open(pts_slave, sizeof(pts_slave));
        if (ptmx < 0) {
            PLOGE("pts_open");
            exit(-1);
        }
    } else {
        pts_slave[0] = '\0';
    }
    // Send atty to daemon - bitfield indicating which streams 
    // are attached to a PTY
    write_int(socketfd, atty);

    // Send the slave path to the daemon
    // (This is "" if we're using FIFOs)
    write_string(socketfd, pts_slave);
    // User ID
    write_int(socketfd, uid);
    // Parent PID
    write_int(socketfd, getppid());

    // Number of command line arguments
    write_int(socketfd, argc);

    // Command line arguments
    int i;
    for (i = 0; i < argc; i++) {
        write_string(socketfd, argv[i]);
    }

    // Wait for acknowledgement from daemon
    read_int(socketfd);

    int outfd, errfd, infd;

    // Open or assign the I/O streams
    if (atty & ATTY_IN) {
        infd = ptmx;
        // Put stdin into raw mode
        set_stdin_raw();
        setup_sighandlers();
    } else {
        infd = open(infile, O_WRONLY);
        if (infd <= 0) {
            PLOGE("infd %s", infile);
            exit(-1);
        }
    }
    if (atty & ATTY_OUT) {
        outfd = ptmx;
        // Forward SIGWINCH
        watch_sigwinch_async(STDOUT_FILENO, outfd);
    } else {
        outfd = open(outfile, O_RDONLY);
        if (outfd <= 0) {
            PLOGE("outfd %s ", outfile);
            exit(-1);
        }
    }
    if (atty & ATTY_ERR) {
        // Do not pump PTY to stderr
        errfd = -1;
    } else {
        errfd = open(errfile, O_RDONLY);
        if (errfd <= 0) {
            PLOGE("errfd %s", errfile);
            exit(-1);
        }
    }

    // Pump I/O to and from the daemon
    pump_async(STDIN_FILENO, infd);
    if (errfd >= 0) {
        pump_async(errfd, STDERR_FILENO);
    }
    pump_ex(outfd, STDOUT_FILENO, 0 /* Don't close output when done */);

    // Cleanup
    restore_stdin();
    watch_sigwinch_cleanup();

    // Get the exit code
    int code = read_int(socketfd);
    LOGD("client exited %d", code);

    return code;
}
