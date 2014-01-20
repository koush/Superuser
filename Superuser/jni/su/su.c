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
#include <sys/uio.h>
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

#include "su.h"
#include "utils.h"

extern int is_daemon;
extern int daemon_from_uid;
extern int daemon_from_pid;

unsigned get_shell_uid() {
  struct passwd* ppwd = getpwnam("shell");
  if (NULL == ppwd) {
    return 2000;
  }

  return ppwd->pw_uid;
}

unsigned get_system_uid() {
  struct passwd* ppwd = getpwnam("system");
  if (NULL == ppwd) {
    return 1000;
  }

  return ppwd->pw_uid;
}

unsigned get_radio_uid() {
  struct passwd* ppwd = getpwnam("radio");
  if (NULL == ppwd) {
    return 1001;
  }

  return ppwd->pw_uid;
}

int fork_zero_fucks() {
    int pid = fork();
    if (pid) {
        int status;
        waitpid(pid, &status, 0);
        return pid;
    }
    else {
        if (pid = fork())
            exit(0);
        return 0;
    }
}

void exec_log(int priority, const char* fmt, ...) {
    static int log_fd = -1;
    struct iovec vec[3];
    va_list args;
    char msg[PATH_MAX];

    if (log_fd < 0) {
        log_fd = open("/dev/log/main", O_WRONLY);
        if (log_fd < 0) {
            return;
        }
    }

    va_start(args, fmt);
    vsnprintf(msg, PATH_MAX, fmt, args);
    va_end(args);

    vec[0].iov_base   = (unsigned char *) &priority;
    vec[0].iov_len    = 1;
    vec[1].iov_base   = (void *) LOG_TAG;
    vec[1].iov_len    = strlen(LOG_TAG) + 1;
    vec[2].iov_base   = (void *) msg;
    vec[2].iov_len    = strlen(msg) + 1;

    writev(log_fd, vec, 3);
}

static int from_init(struct su_initiator *from) {
    char path[PATH_MAX], exe[PATH_MAX];
    char args[4096], *argv0, *argv_rest;
    int fd;
    ssize_t len;
    int i;
    int err;

    from->uid = getuid();
    from->pid = getppid();

    /* Get the command line */
    snprintf(path, sizeof(path), "/proc/%u/cmdline", from->pid);
    fd = open(path, O_RDONLY);
    if (fd < 0) {
        PLOGE("Opening command line");
        return -1;
    }
    len = read(fd, args, sizeof(args));
    err = errno;
    close(fd);
    if (len < 0 || len == sizeof(args)) {
        PLOGEV("Reading command line", err);
        return -1;
    }

    argv0 = args;
    argv_rest = NULL;
    for (i = 0; i < len; i++) {
        if (args[i] == '\0') {
            if (!argv_rest) {
                argv_rest = &args[i+1];
            } else {
                args[i] = ' ';
            }
        }
    }
    args[len] = '\0';

    if (argv_rest) {
        strncpy(from->args, argv_rest, sizeof(from->args));
        from->args[sizeof(from->args)-1] = '\0';
    } else {
        from->args[0] = '\0';
    }

    /* If this isn't app_process, use the real path instead of argv[0] */
    snprintf(path, sizeof(path), "/proc/%u/exe", from->pid);
    len = readlink(path, exe, sizeof(exe));
    if (len < 0) {
        PLOGE("Getting exe path");
        return -1;
    }
    exe[len] = '\0';
    if (strcmp(exe, "/system/bin/app_process")) {
        argv0 = exe;
    }

    strncpy(from->bin, argv0, sizeof(from->bin));
    from->bin[sizeof(from->bin)-1] = '\0';

    struct passwd *pw;
    pw = getpwuid(from->uid);
    if (pw && pw->pw_name) {
        strncpy(from->name, pw->pw_name, sizeof(from->name));
    }

    if (is_daemon) {
        from->uid = daemon_from_uid;
        from->pid = daemon_from_pid;
    }

    return 0;
}

static int get_multiuser_mode() {
    char *data;
    char sdk_ver[PROPERTY_VALUE_MAX];

    data = read_file("/system/build.prop");
    get_property(data, sdk_ver, "ro.build.version.sdk", "0");
    free(data);

    int sdk = atoi(sdk_ver);
    if (sdk < 17)
        return MULTIUSER_MODE_NONE;

    int ret = MULTIUSER_MODE_OWNER_ONLY;
    char mode[12];
    FILE *fp;
    if ((fp = fopen(REQUESTOR_MULTIUSER_MODE, "r"))) {
        fgets(mode, sizeof(mode), fp);
        int last = strlen(mode) - 1;
        if (mode[last] == '\n')
            mode[last] = '\0';
        if (strcmp(mode, MULTIUSER_VALUE_USER) == 0) {
            ret = MULTIUSER_MODE_USER;
        } else if (strcmp(mode, MULTIUSER_VALUE_OWNER_MANAGED) == 0) {
            ret = MULTIUSER_MODE_OWNER_MANAGED;
        }
        else {
            ret = MULTIUSER_MODE_OWNER_ONLY;
        }
        fclose(fp);
    }
    return ret;
}

static void read_options(struct su_context *ctx) {
    ctx->user.multiuser_mode = get_multiuser_mode();
}

static void user_init(struct su_context *ctx) {
    if (ctx->from.uid > 99999) {
        ctx->user.android_user_id = ctx->from.uid / 100000;
        if (ctx->user.multiuser_mode == MULTIUSER_MODE_USER) {
            snprintf(ctx->user.database_path, PATH_MAX, "%s/%d/%s", REQUESTOR_USER_PATH, ctx->user.android_user_id, REQUESTOR_DATABASE_PATH);
            snprintf(ctx->user.base_path, PATH_MAX, "%s/%d/%s", REQUESTOR_USER_PATH, ctx->user.android_user_id, REQUESTOR);
        }
    }
}

static void populate_environment(const struct su_context *ctx) {
    struct passwd *pw;

    if (ctx->to.keepenv)
        return;

    pw = getpwuid(ctx->to.uid);
    if (pw) {
        setenv("HOME", pw->pw_dir, 1);
        if (ctx->to.shell)
            setenv("SHELL", ctx->to.shell, 1);
        else
            setenv("SHELL", DEFAULT_SHELL, 1);
        if (ctx->to.login || ctx->to.uid) {
            setenv("USER", pw->pw_name, 1);
            setenv("LOGNAME", pw->pw_name, 1);
        }
    }
}

void set_identity(unsigned int uid) {
    /*
     * Set effective uid back to root, otherwise setres[ug]id will fail
     * if uid isn't root.
     */
    if (seteuid(0)) {
        PLOGE("seteuid (root)");
        exit(EXIT_FAILURE);
    }
    if (setresgid(uid, uid, uid)) {
        PLOGE("setresgid (%u)", uid);
        exit(EXIT_FAILURE);
    }
    if (setresuid(uid, uid, uid)) {
        PLOGE("setresuid (%u)", uid);
        exit(EXIT_FAILURE);
    }
}

static void socket_cleanup(struct su_context *ctx) {
    if (ctx && ctx->sock_path[0]) {
        if (unlink(ctx->sock_path))
            PLOGE("unlink (%s)", ctx->sock_path);
        ctx->sock_path[0] = 0;
    }
}

/*
 * For use in signal handlers/atexit-function
 * NOTE: su_ctx points to main's local variable.
 *       It's OK due to the program uses exit(3), not return from main()
 */
static struct su_context *su_ctx = NULL;

static void cleanup(void) {
    socket_cleanup(su_ctx);
}

static void cleanup_signal(int sig) {
    socket_cleanup(su_ctx);
    exit(128 + sig);
}

static int socket_create_temp(char *path, size_t len) {
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
    snprintf(path, len, "%s/.socket%d", REQUESTOR_CACHE_PATH, getpid());
    memset(sun.sun_path, 0, sizeof(sun.sun_path));
    snprintf(sun.sun_path, sizeof(sun.sun_path), "%s", path);

    /*
     * Delete the socket to protect from situations when
     * something bad occured previously and the kernel reused pid from that process.
     * Small probability, isn't it.
     */
    unlink(sun.sun_path);

    if (bind(fd, (struct sockaddr*)&sun, sizeof(sun)) < 0) {
        PLOGE("bind");
        goto err;
    }

    if (listen(fd, 1) < 0) {
        PLOGE("listen");
        goto err;
    }

    return fd;
err:
    close(fd);
    return -1;
}

static int socket_accept(int serv_fd) {
    struct timeval tv;
    fd_set fds;
    int fd, rc;

    /* Wait 20 seconds for a connection, then give up. */
    tv.tv_sec = 20;
    tv.tv_usec = 0;
    FD_ZERO(&fds);
    FD_SET(serv_fd, &fds);
    do {
        rc = select(serv_fd + 1, &fds, NULL, NULL, &tv);
    } while (rc < 0 && errno == EINTR);
    if (rc < 1) {
        PLOGE("select");
        return -1;
    }

    fd = accept(serv_fd, NULL, NULL);
    if (fd < 0) {
        PLOGE("accept");
        return -1;
    }

    return fd;
}

static int socket_send_request(int fd, const struct su_context *ctx) {
#define write_data(fd, data, data_len)              \
do {                                                \
    size_t __len = htonl(data_len);                 \
    __len = write((fd), &__len, sizeof(__len));     \
    if (__len != sizeof(__len)) {                   \
        PLOGE("write(" #data ")");                  \
        return -1;                                  \
    }                                               \
    __len = write((fd), data, data_len);            \
    if (__len != data_len) {                        \
        PLOGE("write(" #data ")");                  \
        return -1;                                  \
    }                                               \
} while (0)

#define write_string_data(fd, name, data)        \
do {                                        \
    write_data(fd, name, strlen(name));     \
    write_data(fd, data, strlen(data));     \
} while (0)

// stringify everything.
#define write_token(fd, name, data)         \
do {                                        \
    char buf[16];                           \
    snprintf(buf, sizeof(buf), "%d", data); \
    write_string_data(fd, name, buf);            \
} while (0)

    write_token(fd, "version", PROTO_VERSION);
    write_token(fd, "binary.version", VERSION_CODE);
    write_token(fd, "pid", ctx->from.pid);
    write_string_data(fd, "from.name", ctx->from.name);
    write_string_data(fd, "to.name", ctx->to.name);
    write_token(fd, "from.uid", ctx->from.uid);
    write_token(fd, "to.uid", ctx->to.uid);
    write_string_data(fd, "from.bin", ctx->from.bin);
    // TODO: Fix issue where not using -c does not result a in a command
    write_string_data(fd, "command", get_command(&ctx->to));
    write_token(fd, "eof", PROTO_VERSION);
    return 0;
}

static int socket_receive_result(int fd, char *result, ssize_t result_len) {
    ssize_t len;

    LOGV("waiting for user");
    len = read(fd, result, result_len-1);
    if (len < 0) {
        PLOGE("read(result)");
        return -1;
    }
    result[len] = '\0';

    return 0;
}

static void usage(int status) {
    FILE *stream = (status == EXIT_SUCCESS) ? stdout : stderr;

    fprintf(stream,
    "Usage: su [options] [--] [-] [LOGIN] [--] [args...]\n\n"
    "Options:\n"
    "  --daemon                      start the su daemon agent\n"
    "  -c, --command COMMAND         pass COMMAND to the invoked shell\n"
    "  -h, --help                    display this help message and exit\n"
    "  -, -l, --login                pretend the shell to be a login shell\n"
    "  -m, -p,\n"
    "  --preserve-environment        do not change environment variables\n"
    "  -s, --shell SHELL             use SHELL instead of the default " DEFAULT_SHELL "\n"
    "  -u                            display the multiuser mode and exit\n"
    "  -v, --version                 display version number and exit\n"
    "  -V                            display version code and exit,\n"
    "                                this is used almost exclusively by Superuser.apk\n");
    exit(status);
}

static __attribute__ ((noreturn)) void deny(struct su_context *ctx) {
    char *cmd = get_command(&ctx->to);

    int send_to_app = 1;

    // no need to log if called by root
    if (ctx->from.uid == AID_ROOT)
        send_to_app = 0;

    // dumpstate (which logs to logcat/shell) will spam the crap out of the system with su calls
    if (strcmp("/system/bin/dumpstate", ctx->from.bin) == 0)
        send_to_app = 0;

    if (send_to_app)
        send_result(ctx, DENY);

    LOGW("request rejected (%u->%u %s)", ctx->from.uid, ctx->to.uid, cmd);
    fprintf(stderr, "%s\n", strerror(EACCES));
    exit(EXIT_FAILURE);
}

static __attribute__ ((noreturn)) void allow(struct su_context *ctx) {
    char *arg0;
    int argc, err;

    umask(ctx->umask);
    int send_to_app = 1;

    // no need to log if called by root
    if (ctx->from.uid == AID_ROOT)
        send_to_app = 0;

    // dumpstate (which logs to logcat/shell) will spam the crap out of the system with su calls
    if (strcmp("/system/bin/dumpstate", ctx->from.bin) == 0)
        send_to_app = 0;

    if (send_to_app)
        send_result(ctx, ALLOW);

    char *binary;
    argc = ctx->to.optind;
    if (ctx->to.command) {
        binary = ctx->to.shell;
        ctx->to.argv[--argc] = ctx->to.command;
        ctx->to.argv[--argc] = "-c";
    }
    else if (ctx->to.shell) {
        binary = ctx->to.shell;
    }
    else {
        if (ctx->to.argv[argc]) {
            binary = ctx->to.argv[argc++];
        }
        else {
            binary = DEFAULT_SHELL;
        }
    }

    arg0 = strrchr (binary, '/');
    arg0 = (arg0) ? arg0 + 1 : binary;
    if (ctx->to.login) {
        int s = strlen(arg0) + 2;
        char *p = malloc(s);

        if (!p)
            exit(EXIT_FAILURE);

        *p = '-';
        strcpy(p + 1, arg0);
        arg0 = p;
    }

    populate_environment(ctx);
    set_identity(ctx->to.uid);

#define PARG(arg)                                    \
    (argc + (arg) < ctx->to.argc) ? " " : "",                    \
    (argc + (arg) < ctx->to.argc) ? ctx->to.argv[argc + (arg)] : ""

    LOGD("%u %s executing %u %s using binary %s : %s%s%s%s%s%s%s%s%s%s%s%s%s%s",
            ctx->from.uid, ctx->from.bin,
            ctx->to.uid, get_command(&ctx->to), binary,
            arg0, PARG(0), PARG(1), PARG(2), PARG(3), PARG(4), PARG(5),
            (ctx->to.optind + 6 < ctx->to.argc) ? " ..." : "");

    ctx->to.argv[--argc] = arg0;
    execvp(binary, ctx->to.argv + argc);
    err = errno;
    PLOGE("exec");
    fprintf(stderr, "Cannot execute %s: %s\n", binary, strerror(err));
    exit(EXIT_FAILURE);
}

/*
 * CyanogenMod-specific behavior
 *
 * we can't simply use the property service, since we aren't launched from init
 * and can't trust the location of the property workspace.
 * Find the properties ourselves.
 */
int access_disabled(const struct su_initiator *from) {
#ifndef SUPERUSER_EMBEDDED
    return 0;
#else
    char *data;
    char build_type[PROPERTY_VALUE_MAX];
    char debuggable[PROPERTY_VALUE_MAX], enabled[PROPERTY_VALUE_MAX];
    size_t len;

    data = read_file("/system/build.prop");
    if (check_property(data, "ro.cm.version")) {
        get_property(data, build_type, "ro.build.type", "");
        free(data);

        data = read_file("/default.prop");
        get_property(data, debuggable, "ro.debuggable", "0");
        free(data);
        /* only allow su on debuggable builds */
        if (strcmp("1", debuggable) != 0) {
            LOGE("Root access is disabled on non-debug builds");
            return 1;
        }

        data = read_file("/data/property/persist.sys.root_access");
        if (data != NULL) {
            len = strlen(data);
            if (len >= PROPERTY_VALUE_MAX)
                memcpy(enabled, "1", 2);
            else
                memcpy(enabled, data, len + 1);
            free(data);
        } else
            memcpy(enabled, "1", 2);

        /* enforce persist.sys.root_access on non-eng builds for apps */
        if (strcmp("eng", build_type) != 0 &&
                from->uid != AID_SHELL && from->uid != AID_ROOT &&
                (atoi(enabled) & CM_ROOT_ACCESS_APPS_ONLY) != CM_ROOT_ACCESS_APPS_ONLY ) {
            LOGE("Apps root access is disabled by system setting - "
                 "enable it under settings -> developer options");
            return 1;
        }

        /* disallow su in a shell if appropriate */
        if (from->uid == AID_SHELL &&
                (atoi(enabled) & CM_ROOT_ACCESS_ADB_ONLY) != CM_ROOT_ACCESS_ADB_ONLY ) {
            LOGE("Shell root access is disabled by a system setting - "
                 "enable it under settings -> developer options");
            return 1;
        }

    }
    return 0;
#endif
}

static int get_api_version() {
  char sdk_ver[PROPERTY_VALUE_MAX];
  char *data = read_file("/system/build.prop");
  get_property(data, sdk_ver, "ro.build.version.sdk", "0");
  int ver = atoi(sdk_ver);
  free(data);
  return ver;
}

static void fork_for_samsung(void)
{
    // Samsung CONFIG_SEC_RESTRICT_SETUID wants the parent process to have
    // EUID 0, or else our setresuid() calls will be denied.  So make sure
    // all such syscalls are executed by a child process.
    int rv;

    switch (fork()) {
    case 0:
        return;
    case -1:
        PLOGE("fork");
        exit(1);
    default:
        if (wait(&rv) < 0) {
            exit(1);
        } else {
            exit(WEXITSTATUS(rv));
        }
    }
}

int main(int argc, char *argv[]) {
    return su_main(argc, argv, 1);
}

int su_main(int argc, char *argv[], int need_client) {
    // start up in daemon mode if prompted
    if (argc == 2 && strcmp(argv[1], "--daemon") == 0) {
        return run_daemon();
    }

    fork_for_samsung();

    // Sanitize all secure environment variables (from linker_environ.c in AOSP linker).
    /* The same list than GLibc at this point */
    static const char* const unsec_vars[] = {
        "GCONV_PATH",
        "GETCONF_DIR",
        "HOSTALIASES",
        "LD_AUDIT",
        "LD_DEBUG",
        "LD_DEBUG_OUTPUT",
        "LD_DYNAMIC_WEAK",
        "LD_LIBRARY_PATH",
        "LD_ORIGIN_PATH",
        "LD_PRELOAD",
        "LD_PROFILE",
        "LD_SHOW_AUXV",
        "LD_USE_LOAD_BIAS",
        "LOCALDOMAIN",
        "LOCPATH",
        "MALLOC_TRACE",
        "MALLOC_CHECK_",
        "NIS_PATH",
        "NLSPATH",
        "RESOLV_HOST_CONF",
        "RES_OPTIONS",
        "TMPDIR",
        "TZDIR",
        "LD_AOUT_LIBRARY_PATH",
        "LD_AOUT_PRELOAD",
        // not listed in linker, used due to system() call
        "IFS",
    };
    const char* const* cp   = unsec_vars;
    const char* const* endp = cp + sizeof(unsec_vars)/sizeof(unsec_vars[0]);
    while (cp < endp) {
        unsetenv(*cp);
        cp++;
    }

    /*
     * set LD_LIBRARY_PATH if the linker has wiped out it due to we're suid.
     * This occurs on Android 4.0+
     */
    setenv("LD_LIBRARY_PATH", "/vendor/lib:/system/lib", 0);

    LOGD("su invoked.");

    struct su_context ctx = {
        .from = {
            .pid = -1,
            .uid = 0,
            .bin = "",
            .args = "",
            .name = "",
        },
        .to = {
            .uid = AID_ROOT,
            .login = 0,
            .keepenv = 0,
            .shell = NULL,
            .command = NULL,
            .argv = argv,
            .argc = argc,
            .optind = 0,
            .name = "",
        },
        .user = {
            .android_user_id = 0,
            .multiuser_mode = MULTIUSER_MODE_OWNER_ONLY,
            .database_path = REQUESTOR_DATA_PATH REQUESTOR_DATABASE_PATH,
            .base_path = REQUESTOR_DATA_PATH REQUESTOR
        },
    };
    struct stat st;
    int c, socket_serv_fd, fd;
    char buf[64], *result;
    policy_t dballow;
    struct option long_opts[] = {
        { "command",            required_argument,    NULL, 'c' },
        { "help",            no_argument,        NULL, 'h' },
        { "login",            no_argument,        NULL, 'l' },
        { "preserve-environment",    no_argument,        NULL, 'p' },
        { "shell",            required_argument,    NULL, 's' },
        { "version",            no_argument,        NULL, 'v' },
        { NULL, 0, NULL, 0 },
    };

    while ((c = getopt_long(argc, argv, "+c:hlmps:Vvu", long_opts, NULL)) != -1) {
        switch(c) {
        case 'c':
            ctx.to.shell = DEFAULT_SHELL;
            ctx.to.command = optarg;
            break;
        case 'h':
            usage(EXIT_SUCCESS);
            break;
        case 'l':
            ctx.to.login = 1;
            break;
        case 'm':
        case 'p':
            ctx.to.keepenv = 1;
            break;
        case 's':
            ctx.to.shell = optarg;
            break;
        case 'V':
            printf("%d\n", VERSION_CODE);
            exit(EXIT_SUCCESS);
        case 'v':
            printf("%s\n", VERSION);
            exit(EXIT_SUCCESS);
        case 'u':
            switch (get_multiuser_mode()) {
            case MULTIUSER_MODE_USER:
                printf("%s\n", MULTIUSER_VALUE_USER);
                break;
            case MULTIUSER_MODE_OWNER_MANAGED:
                printf("%s\n", MULTIUSER_VALUE_OWNER_MANAGED);
                break;
            case MULTIUSER_MODE_OWNER_ONLY:
                printf("%s\n", MULTIUSER_VALUE_OWNER_ONLY);
                break;
            case MULTIUSER_MODE_NONE:
                printf("%s\n", MULTIUSER_VALUE_NONE);
                break;
            }
            exit(EXIT_SUCCESS);
        default:
            /* Bionic getopt_long doesn't terminate its error output by newline */
            fprintf(stderr, "\n");
            usage(2);
        }
    }

    if (need_client) {
        // attempt to use the daemon client if not root,
        // or this is api 18 and adb shell (/data is not readable even as root)
        // or just always use it on API 19+ (ART)
        if ((geteuid() != AID_ROOT && getuid() != AID_ROOT) ||
            (get_api_version() >= 18 && getuid() == AID_SHELL) ||
            get_api_version() >= 19) {
            // attempt to connect to daemon...
            LOGD("starting daemon client %d %d", getuid(), geteuid());
            return connect_daemon(argc, argv);
        }
    }

    if (optind < argc && !strcmp(argv[optind], "-")) {
        ctx.to.login = 1;
        optind++;
    }
    /* username or uid */
    if (optind < argc && strcmp(argv[optind], "--")) {
        struct passwd *pw;
        pw = getpwnam(argv[optind]);
        if (!pw) {
            char *endptr;

            /* It seems we shouldn't do this at all */
            errno = 0;
            ctx.to.uid = strtoul(argv[optind], &endptr, 10);
            if (errno || *endptr) {
                LOGE("Unknown id: %s\n", argv[optind]);
                fprintf(stderr, "Unknown id: %s\n", argv[optind]);
                exit(EXIT_FAILURE);
            }
        } else {
            ctx.to.uid = pw->pw_uid;
            if (pw->pw_name)
                strncpy(ctx.to.name, pw->pw_name, sizeof(ctx.to.name));
        }
        optind++;
    }
    if (optind < argc && !strcmp(argv[optind], "--")) {
        optind++;
    }
    ctx.to.optind = optind;

    su_ctx = &ctx;
    if (from_init(&ctx.from) < 0) {
        deny(&ctx);
    }

    read_options(&ctx);
    user_init(&ctx);

    // the latter two are necessary for stock ROMs like note 2 which do dumb things with su, or crash otherwise
    if (ctx.from.uid == AID_ROOT) {
        LOGD("Allowing root/system/radio.");
        allow(&ctx);
    }

    // verify superuser is installed
    if (stat(ctx.user.base_path, &st) < 0) {
        // send to market (disabled, because people are and think this is hijacking their su)
        // if (0 == strcmp(JAVA_PACKAGE_NAME, REQUESTOR))
        //     silent_run("am start -d http://www.clockworkmod.com/superuser/install.html -a android.intent.action.VIEW");
        PLOGE("stat %s", ctx.user.base_path);
        deny(&ctx);
    }

    // odd perms on superuser data dir
    if (st.st_gid != st.st_uid) {
        LOGE("Bad uid/gid %d/%d for Superuser Requestor application",
                (int)st.st_uid, (int)st.st_gid);
        deny(&ctx);
    }

    // always allow if this is the superuser uid
    // superuser needs to be able to reenable itself when disabled...
    if (ctx.from.uid == st.st_uid) {
        allow(&ctx);
    }

    // check if superuser is disabled completely
    if (access_disabled(&ctx.from)) {
        LOGD("access_disabled");
        deny(&ctx);
    }

    // autogrant shell at this point
    if (ctx.from.uid == AID_SHELL) {
        LOGD("Allowing shell.");
        allow(&ctx);
    }

    // deny if this is a non owner request and owner mode only
    if (ctx.user.multiuser_mode == MULTIUSER_MODE_OWNER_ONLY && ctx.user.android_user_id != 0) {
        deny(&ctx);
    }

    ctx.umask = umask(027);

    int ret = mkdir(REQUESTOR_CACHE_PATH, 0770);
    if (chown(REQUESTOR_CACHE_PATH, st.st_uid, st.st_gid)) {
        PLOGE("chown (%s, %ld, %ld)", REQUESTOR_CACHE_PATH, st.st_uid, st.st_gid);
        deny(&ctx);
    }

    if (setgroups(0, NULL)) {
        PLOGE("setgroups");
        deny(&ctx);
    }
    if (setegid(st.st_gid)) {
        PLOGE("setegid (%lu)", st.st_gid);
        deny(&ctx);
    }
    if (seteuid(st.st_uid)) {
        PLOGE("seteuid (%lu)", st.st_uid);
        deny(&ctx);
    }

    dballow = database_check(&ctx);
    switch (dballow) {
        case INTERACTIVE:
            break;
        case ALLOW:
            LOGD("db allowed");
            allow(&ctx);    /* never returns */
        case DENY:
        default:
            LOGD("db denied");
            deny(&ctx);        /* never returns too */
    }

    socket_serv_fd = socket_create_temp(ctx.sock_path, sizeof(ctx.sock_path));
    LOGD(ctx.sock_path);
    if (socket_serv_fd < 0) {
        deny(&ctx);
    }

    signal(SIGHUP, cleanup_signal);
    signal(SIGPIPE, cleanup_signal);
    signal(SIGTERM, cleanup_signal);
    signal(SIGQUIT, cleanup_signal);
    signal(SIGINT, cleanup_signal);
    signal(SIGABRT, cleanup_signal);

    if (send_request(&ctx) < 0) {
        deny(&ctx);
    }

    atexit(cleanup);

    fd = socket_accept(socket_serv_fd);
    if (fd < 0) {
        deny(&ctx);
    }
    if (socket_send_request(fd, &ctx)) {
        deny(&ctx);
    }
    if (socket_receive_result(fd, buf, sizeof(buf))) {
        deny(&ctx);
    }

    close(fd);
    close(socket_serv_fd);
    socket_cleanup(&ctx);

    result = buf;

#define SOCKET_RESPONSE    "socket:"
    if (strncmp(result, SOCKET_RESPONSE, sizeof(SOCKET_RESPONSE) - 1))
        LOGW("SECURITY RISK: Requestor still receives credentials in intent");
    else
        result += sizeof(SOCKET_RESPONSE) - 1;

    if (!strcmp(result, "DENY")) {
        deny(&ctx);
    } else if (!strcmp(result, "ALLOW")) {
        allow(&ctx);
    } else {
        LOGE("unknown response from Superuser Requestor: %s", result);
        deny(&ctx);
    }
}
