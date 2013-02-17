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
#include <sys/wait.h>
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>
#include <paths.h>
#include <stdio.h>
#include <stdarg.h>

#include "su.h"

static void kill_child(pid_t pid)
{
    LOGD("killing child %d", pid);
    if (pid) {
        sigset_t set, old;

        sigemptyset(&set);
        sigaddset(&set, SIGCHLD);
        if (sigprocmask(SIG_BLOCK, &set, &old)) {
            PLOGE("sigprocmask(SIG_BLOCK)");
            return;
        }
        if (kill(pid, SIGKILL))
            PLOGE("kill (%d)", pid);
        else if (sigsuspend(&old) && errno != EINTR)
            PLOGE("sigsuspend");
        if (sigprocmask(SIG_SETMASK, &old, NULL))
            PLOGE("sigprocmask(SIG_BLOCK)");
    }
}

int send_intent(struct su_context *ctx, allow_t allow, const char *action)
{
    const char *socket_path;
    unsigned int uid = ctx->from.uid;
    __sighandler_t handler;
    pid_t pid;

    if (allow == INTERACTIVE) {
        socket_path = ctx->sock_path;
        handler = sigchld_handler;
    } else {
        socket_path = "";
        handler = SIG_IGN;
    }

    pid = fork();
    /* Child */
    if (!pid) {
        char command[ARG_MAX];

        snprintf(command, sizeof(command),
            "exec /system/bin/am broadcast --user %d -a %s --es socket '%s' "
            "--ei caller_uid %d --ei allow %d --es desired_cmd '%s' "
            "--ei all %d --ei version_code %d > /dev/null 2> /dev/null",
            ctx->user.userid, action, socket_path, uid, allow, get_command(&ctx->to),
            ctx->to.all, VERSION_CODE);

        char *args[] = { "sh", "-c", command, NULL, };

        /*
         * before sending the intent, make sure the effective uid/gid match
         * the real uid/gid, otherwise LD_LIBRARY_PATH is wiped
         * in Android 4.0+.
         */
        set_identity(uid);
        int zero = open("/dev/zero", O_RDONLY | O_CLOEXEC);
        dup2(zero, 0);
        int null = open("/dev/null", O_WRONLY | O_CLOEXEC);
        dup2(null, 1);
        dup2(null, 2);
        LOGD("Executing %s\n", command);
        execv(_PATH_BSHELL, args);
        PLOGE("exec am");
        _exit(EXIT_FAILURE);
    }
    
    /* Parent */
    if (pid < 0) {
        PLOGE("fork");
        return -1;
    }
    if (allow != INTERACTIVE) {
        waitpid(pid, NULL, 0);
        signal(SIGCHLD, SIG_DFL);
    }
    return 0;
}
