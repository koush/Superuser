/* Author : Stephen Smalley, <sds@epoch.ncsc.mil> */

/* FLASK */

/*
 * A security context is a set of security attributes
 * associated with each subject and object controlled
 * by the security policy.  Security contexts are
 * externally represented as variable-length strings
 * that can be interpreted by a user or application
 * with an understanding of the security policy. 
 * Internally, the security server uses a simple
 * structure.  This structure is private to the
 * security server and can be changed without affecting
 * clients of the security server.
 */

#ifndef _SEPOL_POLICYDB_CONTEXT_H_
#define _SEPOL_POLICYDB_CONTEXT_H_

#include <stddef.h>
#include <sepol/policydb/ebitmap.h>
#include <sepol/policydb/mls_types.h>

__BEGIN_DECLS

/*
 * A security context consists of an authenticated user
 * identity, a role, a type and a MLS range.
 */
typedef struct context_struct {
	uint32_t user;
	uint32_t role;
	uint32_t type;
	mls_range_t range;
} context_struct_t;

static inline void mls_context_init(context_struct_t * c)
{
	mls_range_init(&c->range);
}

static inline int mls_context_cpy(context_struct_t * dst,
				  context_struct_t * src)
{

	if (mls_range_cpy(&dst->range, &src->range) < 0)
		return -1;

	return 0;
}

static inline int mls_context_cmp(context_struct_t * c1, context_struct_t * c2)
{
	return (mls_level_eq(&c1->range.level[0], &c2->range.level[0]) &&
		mls_level_eq(&c1->range.level[1], &c2->range.level[1]));

}

static inline void mls_context_destroy(context_struct_t * c)
{
	if (c == NULL)
		return;

	mls_range_destroy(&c->range);
	mls_context_init(c);
}

static inline void context_init(context_struct_t * c)
{
	memset(c, 0, sizeof(*c));
}

static inline int context_cpy(context_struct_t * dst, context_struct_t * src)
{
	dst->user = src->user;
	dst->role = src->role;
	dst->type = src->type;
	return mls_context_cpy(dst, src);
}

static inline void context_destroy(context_struct_t * c)
{
	if (c == NULL)
		return;

	c->user = c->role = c->type = 0;
	mls_context_destroy(c);
}

static inline int context_cmp(context_struct_t * c1, context_struct_t * c2)
{
	return ((c1->user == c2->user) &&
		(c1->role == c2->role) &&
		(c1->type == c2->type) && mls_context_cmp(c1, c2));
}

__END_DECLS
#endif
