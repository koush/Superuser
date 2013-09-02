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

static int recv_fd(int sockfd) {
    // Need to receive data from the message, otherwise don't care about it.
    char iovbuf;

    struct iovec iov = {
        .iov_base = &iovbuf,
        .iov_len  = 1,
    };

    char cmsgbuf[CMSG_SPACE(sizeof(int))];

    struct msghdr msg = {
        .msg_iov        = &iov,
        .msg_iovlen     = 1,
        .msg_control    = cmsgbuf,
        .msg_controllen = sizeof(cmsgbuf),
    };

    if (recvmsg(sockfd, &msg, MSG_WAITALL) != 1) {
        goto error;
    }

    // Was a control message actually sent?
    switch (msg.msg_controllen) {
    case 0:
        // No, so the file descriptor was closed and won't be used.
        return -1;
    case sizeof(cmsgbuf):
        // Yes, grab the file descriptor from it.
        break;
    default:
        goto error;
    }

    struct cmsghdr *cmsg = CMSG_FIRSTHDR(&msg);

    if (cmsg             == NULL                  ||
        cmsg->cmsg_len   != CMSG_LEN(sizeof(int)) ||
        cmsg->cmsg_level != SOL_SOCKET            ||
        cmsg->cmsg_type  != SCM_RIGHTS) {
error:
        LOGE("unable to read fd");
        exit(-1);
    }

    return *(int *)CMSG_DATA(cmsg);
}

static void send_fd(int sockfd, int fd) {
    // Need to send some data in the message, this will do.
    struct iovec iov = {
        .iov_base = "",
        .iov_len  = 1,
    };

    struct msghdr msg = {
        .msg_iov        = &iov,
        .msg_iovlen     = 1,
    };

    char cmsgbuf[CMSG_SPACE(sizeof(int))];

    // Is the file descriptor actually open?
    if (fcntl(fd, F_GETFD) == -1) {
        if (errno != EBADF) {
            goto error;
        }
        // It's closed, don't send a control message or sendmsg will EBADF.
    } else {
        // It's open, send the file descriptor in a control message.
        msg.msg_control    = cmsgbuf;
        msg.msg_controllen = sizeof(cmsgbuf);

        struct cmsghdr *cmsg = CMSG_FIRSTHDR(&msg);

        cmsg->cmsg_len   = CMSG_LEN(sizeof(int));
        cmsg->cmsg_level = SOL_SOCKET;
        cmsg->cmsg_type  = SCM_RIGHTS;

        *(int *)CMSG_DATA(cmsg) = fd;
    }

    if (sendmsg(sockfd, &msg, 0) != 1) {
error:
        PLOGE("unable to send fd");
        exit(-1);
    }
}

static int run_daemon_child(int infd, int outfd, int errfd, int argc, char** argv) {
    if (outfd != -1) {
        if (-1 == dup2(outfd, STDOUT_FILENO)) {
            PLOGE("dup2 child outfd");
            exit(-1);
        }
        close(outfd);
    }

    if (errfd != -1) {
        if (-1 == dup2(errfd, STDERR_FILENO)) {
            PLOGE("dup2 child errfd");
            exit(-1);
        }
        close(errfd);
    }

    if (infd != -1) {
        if (-1 == dup2(infd, STDIN_FILENO)) {
            PLOGE("dup2 child infd");
            exit(-1);
        }
        close(infd);
    }

    return main(argc, argv);
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
        daemon_from_pid = credentials.pid;
    }

    int infd  = recv_fd(fd);
    int outfd = recv_fd(fd);
    int errfd = recv_fd(fd);

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

    // ack
    write_int(fd, 1);

    int code;
    // now fork and run main, watch for the child pid exit, and send that
    // across the control channel as the response.
    int child = fork();
    if (child < 0) {
        code = child;
        goto done;
    }

    // if this is the child, steal the terminal,
    // dup2 the received fds with stdin/stdout, and run main, which execs
    // the target.
    if (child == 0) {
        close(fd);

        if (atty) {
            setsid();
            ioctl(infd, TIOCSCTTY, 1);
        }

        return run_daemon_child(infd, outfd, errfd, argc, argv);
    }

    if (infd  != -1) close(infd);
    if (outfd != -1) close(outfd);
    if (errfd != -1) close(errfd);

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
    int uid = getuid();

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

    send_fd(socketfd, STDIN_FILENO);
    send_fd(socketfd, STDOUT_FILENO);
    send_fd(socketfd, STDERR_FILENO);

    write_int(socketfd, argc);

    int i;
    for (i = 0; i < argc; i++) {
        write_string(socketfd, argv[i]);
    }

    // ack
    read_int(socketfd);

    int code = read_int(socketfd);
    LOGD("client exited %d", code);
    return code;
}
