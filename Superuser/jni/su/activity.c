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

// TODO: leverage this with exec_log?
int silent_run(char* command) {
    char *args[] = { "sh", "-c", command, NULL, };
    set_identity(0);
    pid_t pid;
    pid = fork();
    /* Parent */
    if (pid < 0) {
        PLOGE("fork");
        return -1;
    }
    else if (pid > 0) {
        return 0;
    }
    int zero = open("/dev/zero", O_RDONLY | O_CLOEXEC);
    dup2(zero, 0);
    int null = open("/dev/null", O_WRONLY | O_CLOEXEC);
    dup2(null, 1);
    dup2(null, 2);
    execv(_PATH_BSHELL, args);
    PLOGE("exec am");
    _exit(EXIT_FAILURE);
    return -1;
}

int get_owner_login_user_args(struct su_context *ctx, char* user, int user_len) {
    int needs_owner_login_prompt = 0;
    
    if (ctx->user.multiuser_mode == MULTIUSER_MODE_OWNER_MANAGED) {
        if (0 != ctx->user.android_user_id) {
            needs_owner_login_prompt = 1;
        }
        snprintf(user, user_len, "--user 0");
    }
    else if (ctx->user.multiuser_mode == MULTIUSER_MODE_USER) {
        snprintf(user, user_len, "--user %d", ctx->user.android_user_id);
    }
    else if (ctx->user.multiuser_mode == MULTIUSER_MODE_NONE) {
        user[0] = '\0';
    }
    else {
        snprintf(user, user_len, "--user 0");
    }
    
    return needs_owner_login_prompt;
}

int send_result(struct su_context *ctx, policy_t policy) {
    char user[64];
    get_owner_login_user_args(ctx, user, sizeof(user));
    
    if (0 != ctx->user.android_user_id) {
        char user_result_command[ARG_MAX];
        snprintf(user_result_command, sizeof(user_result_command), "exec /system/bin/am " ACTION_RESULT " --ei binary_version %d --es from_name '%s' --es desired_name '%s' --ei uid %d --ei desired_uid %d --es command '%s' --es action %s --user %d",
            VERSION_CODE,
            ctx->from.name, ctx->to.name,
            ctx->from.uid, ctx->to.uid, get_command(&ctx->to), policy == ALLOW ? "allow" : "deny", ctx->user.android_user_id);
        silent_run(user_result_command);
    }

    char result_command[ARG_MAX];
    snprintf(result_command, sizeof(result_command), "exec /system/bin/am " ACTION_RESULT " --ei binary_version %d --es from_name '%s' --es desired_name '%s' --ei uid %d --ei desired_uid %d --es command '%s' --es action %s %s",
        VERSION_CODE,
        ctx->from.name, ctx->to.name,
        ctx->from.uid, ctx->to.uid, get_command(&ctx->to), policy == ALLOW ? "allow" : "deny", user);
    return silent_run(result_command);
}

int send_request(struct su_context *ctx) {
    // if su is operating in MULTIUSER_MODEL_OWNER,
    // and the user requestor is not the owner,
    // the owner needs to be notified of the request.
    // so there will be two activities shown.
    char user[64];
    int needs_owner_login_prompt = get_owner_login_user_args(ctx, user, sizeof(user));

    int ret;
    if (needs_owner_login_prompt) {
        // in multiuser mode, the owner gets the su prompt
        char notify_command[ARG_MAX];

        // start the activity that confirms the request
        snprintf(notify_command, sizeof(notify_command),
            "exec /system/bin/am " ACTION_NOTIFY " --ei caller_uid %d --user %d",
            ctx->from.uid, ctx->user.android_user_id);

        int ret = silent_run(notify_command);
        if (ret) {
            return ret;
        }
    }

    char request_command[ARG_MAX];

    // start the activity that confirms the request
    snprintf(request_command, sizeof(request_command),
        "exec /system/bin/am " ACTION_REQUEST " --es socket '%s' %s",
        ctx->sock_path, user);

    return silent_run(request_command);
}
