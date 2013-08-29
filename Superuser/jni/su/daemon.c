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
#include <termios.h>

#include "su.h"
#include "utils.h"

int is_daemon = 0;
int daemon_from_uid = 0;
int daemon_from_pid = 0;

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

static void pump(int input, int output) {
    char buf[4096];
    int len;
    while ((len = read(input, buf, 4096)) > 0) {
        if (write_blocking(output, buf, len) == -1) break;
    }
    close(input);
    if (output != STDOUT_FILENO) close(output);
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

static int daemon_accept(int fd) {
    is_daemon = 1;
    int pid = read_int(fd);
    LOGD("remote pid: %d", pid);
    int atty = read_int(fd);
    LOGD("remote atty: %d", atty);
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
    char *pts_slave = NULL;

    if (atty) {
        // Get the PTS slave device name
        pts_slave = read_string(fd);
    } else {
        // Using the pipes method
        // Create the FIFOs for the I/O streams
        sprintf(outfile, "%s/%d.stdout", REQUESTOR_DAEMON_PATH, pid);
        sprintf(errfile, "%s/%d.stderr", REQUESTOR_DAEMON_PATH, pid);
        sprintf(infile, "%s/%d.stdin", REQUESTOR_DAEMON_PATH, pid);

        if (mkfifo(outfile, 0660) != 0) {
            PLOGE("mkfifo %s", outfile);
            exit(-1);
        }
        if (mkfifo(errfile, 0660) != 0) {
            PLOGE("mkfifo %s", errfile);
            exit(-1);
        }
        if (mkfifo(infile, 0660) != 0) {
            PLOGE("mkfifo %s", infile);
            exit(-1);
        }

        chown(outfile, daemon_from_uid, 0);
        chown(infile, daemon_from_uid, 0);
        chown(errfile, daemon_from_uid, 0);
        chmod(outfile, 0660);
        chmod(infile, 0660);
        chmod(errfile, 0660);
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

        if (!atty) {
            // Close the child FDs if using pipes
            close(infd);
            close(outfd);
            close(errfd);
        }

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

    if (atty) {
        // Become session leader
        if (setsid() == (pid_t) -1) {
            PLOGE("setsid");
        }

        // Opening the TTY has to occur after the
        // fork() and setsid() so that it becomes
        // our controlling TTY and not the daemon's
        infd = open(pts_slave, O_RDWR);
        if (infd == -1) {
            PLOGE("open(pts_slave) daemon");
            exit(-1);
        }

        // Use the tty for all I/O
        outfd = errfd = infd;
    } else {
        // Open the FIFOs
        outfd = open(outfile, O_WRONLY);
        if (outfd < 0) {
            PLOGE("outfd daemon %s", outfile);
            exit(-1);
        }
        errfd = open(errfile, O_WRONLY);
        if (errfd < 0) {
            PLOGE("errfd daemon %s", errfile);
            exit(-1);
        }
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

/**
 * pts_open
 *
 * open a pts device and returns the name of the slave tty device.
 *
 * arguments
 * slave_name       the name of the slave device
 * slave_name_size  the size of the buffer passed via slave_name
 *
 * return values
 * on failure either -2 or -1 (errno set) is returned.
 * on success, the file descriptor of the master device is returned.
 */
static int pts_open(char *slave_name, size_t slave_name_size) {
    int fdm;
    char *sn_tmp;

    // Open master ptmx device
    fdm = open("/dev/ptmx", O_RDWR);
    if (fdm == -1) return -1;

    // Get the slave name
    sn_tmp = ptsname(fdm);
    if (!sn_tmp) {
        close(fdm);
        return -2;
    }

    strncpy(slave_name, sn_tmp, slave_name_size);
    slave_name[slave_name_size - 1] = '\0';

    // Grant, then unlock
    if (grantpt(fdm) == -1) {
        close(fdm);
        return -1;
    }
    if (unlockpt(fdm) == -1) {
        close(fdm);
        return -1;
    }

    return fdm;
}

static struct termios old_termios;
static int stdin_is_raw = 0;

/**
 * Set stdin to raw mode
 */
static void set_stdin_raw(void) {
    struct termios new_termios;

    // Save the current stdin termios
    if (tcgetattr(STDIN_FILENO, &old_termios) < 0) {
        PLOGE("set_stdin_raw: tcgetattr");
        // Don't terminate - the connectoin could still be useful
        return;
    }

    // Start from the current settings
    new_termios = old_termios;

    // Make the terminal like an SSH or telnet client
    new_termios.c_iflag |= IGNPAR;
    new_termios.c_iflag &= ~(ISTRIP | INLCR | IGNCR | ICRNL | IXON | IXANY | IXOFF);
    new_termios.c_lflag &= ~(ISIG | ICANON | ECHO | ECHOE | ECHOK | ECHONL);
    new_termios.c_oflag &= ~OPOST;
    new_termios.c_cc[VMIN] = 1;
    new_termios.c_cc[VTIME] = 0;

    if (tcsetattr(STDIN_FILENO, TCSAFLUSH, &new_termios) < 0) {
        PLOGE("set_stdin_raw: tcsetattr");
        return;
    }

    stdin_is_raw = 1;
}

/**
 * Restore termios on stdin to the state it was before
 * set_stdin_raw() was called. Does nothing if set_stdin_raw()
 * was never called
 */
static void restore_stdin(void) {
    if (!stdin_is_raw) return;

    if (tcsetattr(STDIN_FILENO, TCSAFLUSH, &old_termios) < 0) {
        PLOGE("restore_stdin");
        return;
    }
}

int connect_daemon(int argc, char *argv[]) {
    char errfile[PATH_MAX];
    char outfile[PATH_MAX];
    char infile[PATH_MAX];
    int uid = getuid();
    int ptmx;
    char pts_slave[PATH_MAX];

    int atty = isatty(STDOUT_FILENO);

    if (!atty) {
        // If we're not an interactive terminal, use pipes
        sprintf(outfile, "%s/%d.stdout", REQUESTOR_DAEMON_PATH, getpid());
        sprintf(errfile, "%s/%d.stderr", REQUESTOR_DAEMON_PATH, getpid());
        sprintf(infile, "%s/%d.stdin", REQUESTOR_DAEMON_PATH, getpid());
        unlink(errfile);
        unlink(infile);
        unlink(outfile);
    }

    struct sockaddr_un sun;

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
    write_int(socketfd, getpid());
    write_int(socketfd, atty);
    write_int(socketfd, uid);
    write_int(socketfd, getppid());
    write_int(socketfd, argc);

    int i;
    for (i = 0; i < argc; i++) {
        write_string(socketfd, argv[i]);
    }

    // Open a PTS device and send the slave path to the daemon
    if (atty) {
        ptmx = pts_open(pts_slave, sizeof(pts_slave));
        if (ptmx < 0) {
            PLOGE("pts_open");
            exit(-1);
        }
        write_string(socketfd, pts_slave);
    }

    // ack
    read_int(socketfd);

    int outfd, errfd, infd;

    if (atty) {
        // Redirect our I/O streams to the PTS master
        outfd = errfd = infd = ptmx;
    } else {
        outfd = open(outfile, O_RDONLY);
        if (outfd <= 0) {
            PLOGE("outfd %s ", outfile);
            exit(-1);
        }
        errfd = open(errfile, O_RDONLY);
        if (errfd <= 0) {
            PLOGE("errfd %s", errfile);
            exit(-1);
        }
        infd = open(infile, O_WRONLY);
        if (infd <= 0) {
            PLOGE("infd %s", infile);
            exit(-1);
        }
    }

    // If stdin is a tty, we should put it into raw mode
    // otherwise ncurses apps are going to misbehave
    if (isatty(STDIN_FILENO)) {
        set_stdin_raw();
    }

    signal(SIGPIPE, SIG_IGN);

    pump_async(STDIN_FILENO, infd);
    if (!atty) {
        // Ignore our own stderr if dealing with a terminal device
        pump_async(errfd, STDERR_FILENO);
    }
    pump(outfd, STDOUT_FILENO);

    // Get the exit code
    int code;
    if (read(socketfd, &code, sizeof(int)) != sizeof(int)) {
        LOGE("unable to read exit code");
    }

    restore_stdin();
    LOGD("client exited %d", code);
    return code;
}
