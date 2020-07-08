/* Author: Karl MacMillan <kmacmillan@mentalrootkit.com> */

#ifndef __sepol_errno_h__
#define __sepol_errno_h__

#include <errno.h>
#include <sys/cdefs.h>

__BEGIN_DECLS

#define SEPOL_OK             0

/* These first error codes are defined for compatibility with
 * previous version of libsepol. In the future, custome error
 * codes that don't map to system error codes should be defined
 * outside of the range of system error codes.
 */
#define SEPOL_ERR            -1
#define SEPOL_ENOTSUP        -2  /* feature not supported in module language */
#define SEPOL_EREQ           -3  /* requirements not met */

/* Error codes that map to system error codes */
#define SEPOL_ENOMEM         -ENOMEM
#define SEPOL_ERANGE         -ERANGE
#define SEPOL_EEXIST         -EEXIST
#define SEPOL_ENOENT         -ENOENT

__END_DECLS
#endif
