#include <unistd.h>
#include <sys/types.h>
#include <stdlib.h>
#include <errno.h>
#include "selinux_internal.h"
#include <selinux/avc.h>

static pthread_once_t once = PTHREAD_ONCE_INIT;
static int selinux_enabled;

static void avc_init_once(void)
{
	selinux_enabled = is_selinux_enabled();
	if (selinux_enabled == 1)
		avc_open(NULL, 0);
}

int selinux_check_access(const char * scon, const char * tcon, const char *class, const char *perm, void *aux) {
	int status = -1;
	int rc = -1;
	security_id_t scon_id;
	security_id_t tcon_id;
	security_class_t sclass;
	access_vector_t av;

	__selinux_once(once, avc_init_once);

	if (selinux_enabled != 1)
		return 0;

	if ((rc = avc_context_to_sid(scon, &scon_id)) < 0)  return rc;

	if ((rc = avc_context_to_sid(tcon, &tcon_id)) < 0)  return rc;

	if ((sclass = string_to_security_class(class)) == 0) return status;

	if ((av = string_to_av_perm(sclass, perm)) == 0) return status;

	return avc_has_perm (scon_id, tcon_id, sclass, av, NULL, aux);
}

