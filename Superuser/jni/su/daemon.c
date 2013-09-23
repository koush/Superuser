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

#define _GNU_SOURCE /* for unshare() */

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
#include <sys/mount.h>
#include <sys/stat.h>
#include <stdarg.h>
#include <sys/types.h>
#include <pthread.h>
#include <sched.h>
#include <termios.h>
#include <cutils/multiuser.h>

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

static void mount_emulated_storage(int user_id) {
    const char *emulated_source = getenv("EMULATED_STORAGE_SOURCE");
    const char *emulated_target = getenv("EMULATED_STORAGE_TARGET");
    const char* legacy = getenv("EXTERNAL_STORAGE");

    if (!emulated_source || !emulated_target) {
        // No emulated storage is present
        return;
    }

    // Create a second private mount namespace for our process
    if (unshare(CLONE_NEWNS) < 0) {
        PLOGE("unshare");
        return;
    }

    if (mount("rootfs", "/", NULL, MS_SLAVE | MS_REC, NULL) < 0) {
        PLOGE("mount rootfs as slave");
        return;
    }

    // /mnt/shell/emulated -> /storage/emulated
    if (mount(emulated_source, emulated_target, NULL, MS_BIND, NULL) < 0) {
        PLOGE("mount emulated storage");
    }

    char target_user[PATH_MAX];
    snprintf(target_user, PATH_MAX, "%s/%d", emulated_target, user_id);

    // /mnt/shell/emulated/<user> -> /storage/emulated/legacy
    if (mount(target_user, legacy, NULL, MS_BIND | MS_REC, NULL) < 0) {
        PLOGE("mount legacy path");
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

    int mount_storage = read_int(fd);
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

        if (mount_storage) {
            mount_emulated_storage(multiuser_get_user_id(daemon_from_uid));
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

    int mount_storage = getenv("MOUNT_EMULATED_STORAGE") != NULL;

    write_int(socketfd, getpid());
    write_int(socketfd, isatty(STDIN_FILENO));
    write_int(socketfd, uid);
    write_int(socketfd, getppid());
    write_int(socketfd, mount_storage);
    write_int(socketfd, mount_storage ? argc - 1 : argc);

    int i;
    for (i = 0; i < argc; i++) {
        if (i == 1 && mount_storage) {
            continue;
        }
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
