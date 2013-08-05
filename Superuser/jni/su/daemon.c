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
    if (len < sizeof(int)) {
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
    if (len > PATH_MAX) {
        LOGE("string too long");
        exit(-1);
    }
    char* val = malloc(sizeof(char) * (len + 1));
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

static void pump(int input, int output) {
    char buf[4096];
    int len;
    while ((len = read(input, buf, 4096)) > 0) {
        write(output, buf, len);
    }
    close(input);
    close(output);
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
    int argc = read_int(fd);
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

    // ack
    write_int(fd, 1);

    int ptm = -1;
    char* devname = NULL;
    if (atty) {
        ptm = open("/dev/ptmx", O_RDWR);
        if (ptm <= 0) {
            PLOGE("ptm");
            exit(-1);
        }
        if(grantpt(ptm) || unlockpt(ptm) || ((devname = (char*) ptsname(ptm)) == 0)) {
            PLOGE("ptm setup");
            close(ptm);
            exit(-1);
        }
        LOGD("devname: %s", devname);
    }

    int outfd = open(outfile, O_WRONLY);
    if (outfd <= 0) {
        PLOGE("outfd daemon %s", outfile);
        goto done;
    }
    int errfd = open(errfile, O_WRONLY);
    if (errfd <= 0) {
        PLOGE("errfd daemon %s", errfile);
        goto done;
    }
    int infd = open(infile, O_RDONLY);
    if (infd <= 0) {
        PLOGE("infd daemon %s", infile);
        goto done;
    }

    int code;
    // now fork and run main, watch for the child pid exit, and send that
    // across the control channel as the response.
    int child = fork();
    if (child < 0) {
        code = child;
        goto done;
    }

    // if this is the child, open the fifo streams
    // and dup2 them with stdin/stdout, and run main, which execs
    // the target.
    if (child == 0) {
        close(fd);

        if (devname != NULL) {
            int pts = open(devname, O_RDWR);
            if(pts < 0) {
                PLOGE("pts");
                exit(-1);
            }

            struct termios slave_orig_term_settings; // Saved terminal settings 
            tcgetattr(pts, &slave_orig_term_settings);

            struct termios new_term_settings;
            new_term_settings = slave_orig_term_settings; 
            cfmakeraw(&new_term_settings);
            // WHY DOESN'T THIS WORK, FUUUUU
            new_term_settings.c_lflag &= ~(ECHO);
            tcsetattr(pts, TCSANOW, &new_term_settings);

            setsid();
            ioctl(pts, TIOCSCTTY, 1);

            close(infd);
            close(outfd);
            close(errfd);
            close(ptm);

            errfd = pts;
            infd = pts;
            outfd = pts;
        }

        return run_daemon_child(infd, outfd, errfd, argc, argv);
    }

    if (devname != NULL) {
        // pump ptm across the socket
        pump_async(infd, ptm);
        pump(ptm, outfd);
    }
    else {
        close(infd);
        close(outfd);
        close(errfd);
    }

    // wait for the child to exit, and send the exit code
    // across the wire.
    int status;
    LOGD("waiting for child exit");
    if (waitpid(child, &status, 0) > 0) {
        code = WEXITSTATUS(status);
    }
    else {
        code = -1;
    }

done:
    write(fd, &code, sizeof(int));
    close(fd);
    LOGD("child exited");
    return code;
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

int connect_daemon(int argc, char *argv[]) {
    char errfile[PATH_MAX];
    char outfile[PATH_MAX];
    char infile[PATH_MAX];
    int uid = getuid();
    sprintf(outfile, "%s/%d.stdout", REQUESTOR_DAEMON_PATH, getpid());
    sprintf(errfile, "%s/%d.stderr", REQUESTOR_DAEMON_PATH, getpid());
    sprintf(infile, "%s/%d.stdin", REQUESTOR_DAEMON_PATH, getpid());
    unlink(errfile);
    unlink(infile);
    unlink(outfile);

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
    write_int(socketfd, isatty(STDIN_FILENO));
    write_int(socketfd, uid);
    write_int(socketfd, getppid());
    write_int(socketfd, argc);

    int i;
    for (i = 0; i < argc; i++) {
        write_string(socketfd, argv[i]);
    }

    // ack
    read_int(socketfd);

    int outfd = open(outfile, O_RDONLY);
    if (outfd <= 0) {
        PLOGE("outfd %s ", outfile);
        exit(-1);
    }
    int errfd = open(errfile, O_RDONLY);
    if (errfd <= 0) {
        PLOGE("errfd %s", errfile);
        exit(-1);
    }
    int infd = open(infile, O_WRONLY);
    if (infd <= 0) {
        PLOGE("infd %s", infile);
        exit(-1);
    }

    pump_async(STDIN_FILENO, infd);
    pump_async(errfd, STDERR_FILENO);
    pump(outfd, STDOUT_FILENO);

    int code = read_int(socketfd);
    LOGD("client exited %d", code);
    return code;
}
