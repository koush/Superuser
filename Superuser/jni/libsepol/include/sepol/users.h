#ifndef _SEPOL_USERS_H_
#define _SEPOL_USERS_H_

#include <sepol/policydb.h>
#include <sepol/user_record.h>
#include <sepol/handle.h>
#include <stddef.h>
#include <sys/cdefs.h>

__BEGIN_DECLS

/*---------compatibility------------*/

/* Given an existing binary policy (starting at 'data with length 'len')
   and user configurations living in 'usersdir', generate a new binary
   policy for the new user configurations.  Sets '*newdata' and '*newlen'
   to refer to the new binary policy image. */
extern int sepol_genusers(void *data, size_t len,
			  const char *usersdir,
			  void **newdata, size_t * newlen);

/* Enable or disable deletion of users by sepol_genusers(3) when
   a user in original binary policy image is not defined by the
   new user configurations.  Defaults to disabled. */
extern void sepol_set_delusers(int on);

/*--------end compatibility----------*/

/* Modify the user, or add it, if the key is not found */
extern int sepol_user_modify(sepol_handle_t * handle,
			     sepol_policydb_t * policydb,
			     const sepol_user_key_t * key,
			     const sepol_user_t * data);

/* Return the number of users */
extern int sepol_user_count(sepol_handle_t * handle,
			    const sepol_policydb_t * p, unsigned int *response);

/* Check if the specified user exists */
extern int sepol_user_exists(sepol_handle_t * handle,
			     const sepol_policydb_t * policydb,
			     const sepol_user_key_t * key, int *response);

/* Query a user - returns the user or NULL if not found */
extern int sepol_user_query(sepol_handle_t * handle,
			    const sepol_policydb_t * p,
			    const sepol_user_key_t * key,
			    sepol_user_t ** response);

/* Iterate the users
 * The handler may return:
 * -1 to signal an error condition,
 * 1 to signal successful exit
 * 0 to signal continue */
extern int sepol_user_iterate(sepol_handle_t * handle,
			      const sepol_policydb_t * policydb,
			      int (*fn) (const sepol_user_t * user,
					 void *fn_arg), void *arg);

__END_DECLS
#endif
