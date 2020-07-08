#ifndef _SEPOL_POLICYDB_POLCAPS_H_
#define _SEPOL_POLICYDB_POLCAPS_H_

#include <sys/cdefs.h>

__BEGIN_DECLS

/* Policy capabilities */
enum {
	POLICYDB_CAPABILITY_NETPEER,
	POLICYDB_CAPABILITY_OPENPERM,
	POLICYDB_CAPABILITY_REDHAT1, /* reserved for RH testing of ptrace_child */
	POLICYDB_CAPABILITY_ALWAYSNETWORK,
	__POLICYDB_CAPABILITY_MAX
};
#define POLICYDB_CAPABILITY_MAX (__POLICYDB_CAPABILITY_MAX - 1)

/* Convert a capability name to number. */
extern int sepol_polcap_getnum(const char *name);

/* Convert a capability number to name. */
extern const char *sepol_polcap_getname(int capnum);

__END_DECLS
#endif /* _SEPOL_POLICYDB_POLCAPS_H_ */
