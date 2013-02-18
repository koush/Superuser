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

#ifndef SU_h 
#define SU_h 1

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "su"

#ifndef AID_SHELL
#define AID_SHELL (get_shell_uid())
#endif

#ifndef AID_ROOT
#define AID_ROOT  0
#endif

// CyanogenMod-specific behavior
#define CM_ROOT_ACCESS_DISABLED      0
#define CM_ROOT_ACCESS_APPS_ONLY     1
#define CM_ROOT_ACCESS_ADB_ONLY      2
#define CM_ROOT_ACCESS_APPS_AND_ADB  3

#define REQUESTOR "com.koushikdutta.superuser"
#define REQUESTOR_DATA_PATH "/data/data/"
#define REQUESTOR_FILES_PATH REQUESTOR_DATA_PATH REQUESTOR "/files"
#define REQUESTOR_USER_PATH "/data/user/"
#define REQUESTOR_CACHE_PATH "/dev/" REQUESTOR

// there's no guarantee that the db or files are actually created named as such by
// SQLiteOpenHelper, etc. Though that is the behavior as of current.
// it is up to the Android application to symlink as appropriate.
#define REQUESTOR_DATABASE_PATH REQUESTOR "/databases/su.sqlite"
#define REQUESTOR_MULTIUSER_MODE REQUESTOR_FILES_PATH "/multiuser_mode"

/* intent actions */
#define ACTION_REQUEST "start -n " REQUESTOR "/.RequestActivity"
#define ACTION_NOTIFY "start -n " REQUESTOR "/.NotifyActivity"
#define ACTION_RESULT "broadcast -n " REQUESTOR "/.SuReceiver"

#define DEFAULT_SHELL "/system/bin/sh"

#ifdef SU_LEGACY_BUILD
#define VERSION_EXTRA	"l"
#else
#define VERSION_EXTRA	""
#endif

#define VERSION "3.3" VERSION_EXTRA
#define VERSION_CODE 19

#define DATABASE_VERSION 6
#define PROTO_VERSION 0

struct su_initiator {
    pid_t pid;
    unsigned uid;
    unsigned user;
    char bin[PATH_MAX];
    char args[4096];
};

struct su_request {
    unsigned uid;
    int login;
    int keepenv;
    char *shell;
    char *command;
    char **argv;
    int argc;
    int optind;
    int appId;
    int all;
};

struct su_user_info {
    unsigned android_user_id;
    int multiuser_mode;
    char database_path[PATH_MAX];
};

struct su_context {
    struct su_initiator from;
    struct su_request to;
    struct su_user_info user;
    mode_t umask;
    char sock_path[PATH_MAX];
};

// multiuser su behavior
typedef enum {
  // only owner can su
  MULTIUSER_MODE_OWNER_ONLY = 0,
  // owner gets a su prompt
  MULTIUSER_MODE_OWNER = 1,
  // user gets a su prompt
  MULTIUSER_MODE_USER = 2,
} multiuser_mode_t;

typedef enum {
    INTERACTIVE = 0,
    DENY = 1,
    ALLOW = 2,
} policy_t;

extern policy_t database_check(struct su_context *ctx);
extern void set_identity(unsigned int uid);
extern int send_request(struct su_context *ctx);
extern int send_result(struct su_context *ctx, policy_t policy);
extern void sigchld_handler(int sig);

static inline char *get_command(const struct su_request *to)
{
	return (to->command) ? to->command : to->shell;
}

void exec_loge(const char* fmt, ...);
void exec_logw(const char* fmt, ...);
void exec_logd(const char* fmt, ...);

// fallback to using /system/bin/log.
// can't use liblog.so because this is a static binary.
#ifndef LOGE
#define LOGE exec_loge
#endif
#ifndef LOGD
#define LOGD exec_logd
#endif
#ifndef LOGW
#define LOGW exec_logw
#endif

#if 0
#undef LOGE
#define LOGE(fmt,args...) fprintf(stderr, fmt, ##args)
#undef LOGD
#define LOGD(fmt,args...) fprintf(stderr, fmt, ##args)
#undef LOGW
#define LOGW(fmt,args...) fprintf(stderr, fmt, ##args)
#endif

#include <errno.h>
#include <string.h>
#define PLOGE(fmt,args...) LOGE(fmt " failed with %d: %s", ##args, errno, strerror(errno))
#define PLOGEV(fmt,err,args...) LOGE(fmt " failed with %d: %s", ##args, err, strerror(err))

#endif
