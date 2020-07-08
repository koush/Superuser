#ifndef _SELINUX_ANDROID_H_
#define _SELINUX_ANDROID_H_

#include <stdbool.h>
#include <sys/types.h>
#include <unistd.h>

#include <selinux/label.h>

#ifdef __cplusplus
extern "C" {
#endif

extern struct selabel_handle* selinux_android_file_context_handle(void);

extern struct selabel_handle* selinux_android_service_context_handle(void);

extern void selinux_android_set_sehandle(const struct selabel_handle *hndl);

extern int selinux_android_load_policy(void);

extern int selinux_android_reload_policy(void);

extern int selinux_android_setcontext(uid_t uid,
				      int isSystemServer,
				      const char *seinfo,
				      const char *name);

extern int selinux_android_setfilecon(const char *pkgdir,
				       const char *pkgname,
				       const char *seinfo,
				       uid_t uid);

extern int selinux_log_callback(int type, const char *fmt, ...)
    __attribute__ ((format(printf, 2, 3)));

#define SELINUX_ANDROID_RESTORECON_NOCHANGE 1
#define SELINUX_ANDROID_RESTORECON_VERBOSE  2
#define SELINUX_ANDROID_RESTORECON_RECURSE  4
#define SELINUX_ANDROID_RESTORECON_FORCE    8
#define SELINUX_ANDROID_RESTORECON_DATADATA 16
extern int selinux_android_restorecon(const char *file, unsigned int flags);

extern int selinux_android_restorecon_pkgdir(const char *pkgdir,
                                             const char *seinfo,
                                             uid_t uid,
                                             unsigned int flags);

extern int selinux_android_seapp_context_reload(void);

extern bool selinux_android_use_data_policy(void);

#ifdef __cplusplus
}
#endif
#endif
