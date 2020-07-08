#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

#include "context_internal.h"
#include "debug.h"

struct sepol_context {

	/* Selinux user */
	char *user;

	/* Selinux role */
	char *role;

	/* Selinux type */
	char *type;

	/* MLS */
	char *mls;
};

/* User */
const char *sepol_context_get_user(const sepol_context_t * con)
{

	return con->user;
}

hidden_def(sepol_context_get_user)

int sepol_context_set_user(sepol_handle_t * handle,
			   sepol_context_t * con, const char *user)
{

	char *tmp_user = strdup(user);
	if (!tmp_user) {
		ERR(handle, "out of memory, could not set "
		    "context user to %s", user);
		return STATUS_ERR;
	}

	free(con->user);
	con->user = tmp_user;
	return STATUS_SUCCESS;
}

hidden_def(sepol_context_set_user)

/* Role */
const char *sepol_context_get_role(const sepol_context_t * con)
{

	return con->role;
}

hidden_def(sepol_context_get_role)

int sepol_context_set_role(sepol_handle_t * handle,
			   sepol_context_t * con, const char *role)
{

	char *tmp_role = strdup(role);
	if (!tmp_role) {
		ERR(handle, "out of memory, could not set "
		    "context role to %s", role);
		return STATUS_ERR;
	}
	free(con->role);
	con->role = tmp_role;
	return STATUS_SUCCESS;
}

hidden_def(sepol_context_set_role)

/* Type */
const char *sepol_context_get_type(const sepol_context_t * con)
{

	return con->type;
}

hidden_def(sepol_context_get_type)

int sepol_context_set_type(sepol_handle_t * handle,
			   sepol_context_t * con, const char *type)
{

	char *tmp_type = strdup(type);
	if (!tmp_type) {
		ERR(handle, "out of memory, could not set "
		    "context type to %s", type);
		return STATUS_ERR;
	}
	free(con->type);
	con->type = tmp_type;
	return STATUS_SUCCESS;
}

hidden_def(sepol_context_set_type)

/* MLS */
const char *sepol_context_get_mls(const sepol_context_t * con)
{

	return con->mls;
}

hidden_def(sepol_context_get_mls)

int sepol_context_set_mls(sepol_handle_t * handle,
			  sepol_context_t * con, const char *mls)
{

	char *tmp_mls = strdup(mls);
	if (!tmp_mls) {
		ERR(handle, "out of memory, could not set "
		    "MLS fields to %s", mls);
		return STATUS_ERR;
	}
	free(con->mls);
	con->mls = tmp_mls;
	return STATUS_SUCCESS;
}

hidden_def(sepol_context_set_mls)

/* Create */
int sepol_context_create(sepol_handle_t * handle, sepol_context_t ** con_ptr)
{

	sepol_context_t *con =
	    (sepol_context_t *) malloc(sizeof(sepol_context_t));

	if (!con) {
		ERR(handle, "out of memory, could not " "create context\n");
		return STATUS_ERR;
	}

	con->user = NULL;
	con->role = NULL;
	con->type = NULL;
	con->mls = NULL;
	*con_ptr = con;
	return STATUS_SUCCESS;
}

hidden_def(sepol_context_create)

/* Deep copy clone */
int sepol_context_clone(sepol_handle_t * handle,
			const sepol_context_t * con, sepol_context_t ** con_ptr)
{

	sepol_context_t *new_con = NULL;

	if (!con) {
		*con_ptr = NULL;
		return 0;
	}
	  
	if (sepol_context_create(handle, &new_con) < 0)
		goto err;

	if (!(new_con->user = strdup(con->user)))
		goto omem;

	if (!(new_con->role = strdup(con->role)))
		goto omem;

	if (!(new_con->type = strdup(con->type)))
		goto omem;

	if (con->mls && !(new_con->mls = strdup(con->mls)))
		goto omem;

	*con_ptr = new_con;
	return STATUS_SUCCESS;

      omem:
	ERR(handle, "out of memory");

      err:
	ERR(handle, "could not clone context record");
	sepol_context_free(new_con);
	return STATUS_ERR;
}

hidden_def(sepol_context_clone)

/* Destroy */
void sepol_context_free(sepol_context_t * con)
{

	if (!con)
		return;

	free(con->user);
	free(con->role);
	free(con->type);
	free(con->mls);
	free(con);
}

hidden_def(sepol_context_free)

int sepol_context_from_string(sepol_handle_t * handle,
			      const char *str, sepol_context_t ** con)
{

	char *tmp = NULL, *low, *high;
	sepol_context_t *tmp_con = NULL;

	if (!strcmp(str, "<<none>>")) {
		*con = NULL;
		return STATUS_SUCCESS;
	}

	if (sepol_context_create(handle, &tmp_con) < 0)
		goto err;

	/* Working copy context */
	tmp = strdup(str);
	if (!tmp) {
		ERR(handle, "out of memory");
		goto err;
	}
	low = tmp;

	/* Then, break it into its components */

	/* User */
	if (!(high = strchr(low, ':')))
		goto mcontext;
	else
		*high++ = '\0';
	if (sepol_context_set_user(handle, tmp_con, low) < 0)
		goto err;
	low = high;

	/* Role */
	if (!(high = strchr(low, ':')))
		goto mcontext;
	else
		*high++ = '\0';
	if (sepol_context_set_role(handle, tmp_con, low) < 0)
		goto err;
	low = high;

	/* Type, and possibly MLS */
	if (!(high = strchr(low, ':'))) {
		if (sepol_context_set_type(handle, tmp_con, low) < 0)
			goto err;
	} else {
		*high++ = '\0';
		if (sepol_context_set_type(handle, tmp_con, low) < 0)
			goto err;
		low = high;
		if (sepol_context_set_mls(handle, tmp_con, low) < 0)
			goto err;
	}

	free(tmp);
	*con = tmp_con;

	return STATUS_SUCCESS;

      mcontext:
	errno = EINVAL;
	ERR(handle, "malformed context \"%s\"", str);

      err:
	ERR(handle, "could not construct context from string");
	free(tmp);
	sepol_context_free(tmp_con);
	return STATUS_ERR;
}

hidden_def(sepol_context_from_string)

int sepol_context_to_string(sepol_handle_t * handle,
			    const sepol_context_t * con, char **str_ptr)
{

	int rc;
	const int user_sz = strlen(con->user);
	const int role_sz = strlen(con->role);
	const int type_sz = strlen(con->type);
	const int mls_sz = (con->mls) ? strlen(con->mls) : 0;
	const int total_sz = user_sz + role_sz + type_sz +
	    mls_sz + ((con->mls) ? 3 : 2);

	char *str = (char *)malloc(total_sz + 1);
	if (!str)
		goto omem;

	if (con->mls) {
		rc = snprintf(str, total_sz + 1, "%s:%s:%s:%s",
			      con->user, con->role, con->type, con->mls);
		if (rc < 0 || (rc >= total_sz + 1)) {
			ERR(handle, "print error");
			goto err;
		}
	} else {
		rc = snprintf(str, total_sz + 1, "%s:%s:%s",
			      con->user, con->role, con->type);
		if (rc < 0 || (rc >= total_sz + 1)) {
			ERR(handle, "print error");
			goto err;
		}
	}

	*str_ptr = str;
	return STATUS_SUCCESS;

      omem:
	ERR(handle, "out of memory");

      err:
	ERR(handle, "could not convert context to string");
	free(str);
	return STATUS_ERR;
}
