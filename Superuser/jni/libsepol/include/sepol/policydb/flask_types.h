/* -*- linux-c -*- */

/*
 * Author : Stephen Smalley, <sds@epoch.ncsc.mil> 
 */

#ifndef _SEPOL_POLICYDB_FLASK_TYPES_H_
#define _SEPOL_POLICYDB_FLASK_TYPES_H_

/*
 * The basic Flask types and constants.
 */

#include <sys/types.h>
#include <stdint.h>
#include <sys/cdefs.h>

__BEGIN_DECLS

/*
 * A security context is a set of security attributes 
 * associated with each subject and object controlled
 * by the security policy.  The security context type
 * is defined as a variable-length string that can be
 * interpreted by any application or user with an 
 * understanding of the security policy.
 */
typedef char *sepol_security_context_t;

/*
 * An access vector (AV) is a collection of related permissions
 * for a pair of SIDs.  The bits within an access vector
 * are interpreted differently depending on the class of
 * the object.  The access vector interpretations are specified
 * in flask/access_vectors, and the corresponding constants
 * for permissions are defined in the automatically generated
 * header file av_permissions.h.
 */
typedef uint32_t sepol_access_vector_t;

/*
 * Each object class is identified by a fixed-size value.
 * The set of security classes is specified in flask/security_classes, 
 * with the corresponding constants defined in the automatically 
 * generated header file flask.h.
 */
typedef uint16_t sepol_security_class_t;
#define SEPOL_SECCLASS_NULL			0x0000	/* no class */

#define SELINUX_MAGIC 0xf97cff8c
#define SELINUX_MOD_MAGIC 0xf97cff8d

typedef uint32_t sepol_security_id_t;
#define SEPOL_SECSID_NULL 0

struct sepol_av_decision {
	sepol_access_vector_t allowed;
	sepol_access_vector_t decided;
	sepol_access_vector_t auditallow;
	sepol_access_vector_t auditdeny;
	uint32_t seqno;
};

__END_DECLS
#endif
