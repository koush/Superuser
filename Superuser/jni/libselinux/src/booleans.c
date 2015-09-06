/*
 * Author: Karl MacMillan <kmacmillan@tresys.com>
 *
 * Modified:  
 *   Dan Walsh <dwalsh@redhat.com> - Added security_load_booleans().
 */

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <assert.h>
#include <stdlib.h>
#include <dirent.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>
#include <fnmatch.h>
#include <limits.h>
#include <ctype.h>
#include <errno.h>

#include "selinux_internal.h"
#include "policy.h"

#define SELINUX_BOOL_DIR "/booleans/"

static int filename_select(const struct dirent *d)
{
	if (d->d_name[0] == '.'
	    && (d->d_name[1] == '\0'
		|| (d->d_name[1] == '.' && d->d_name[2] == '\0')))
		return 0;
	return 1;
}

int security_get_boolean_names(char ***names, int *len)
{
	char path[PATH_MAX];
	int i, rc;
	struct dirent **namelist;
	char **n;

	assert(len);
	if (!selinux_mnt) {
		errno = ENOENT;
		return -1;
	}

	snprintf(path, sizeof path, "%s%s", selinux_mnt, SELINUX_BOOL_DIR);
	*len = scandir(path, &namelist, &filename_select, alphasort);
	if (*len <= 0) {
		return -1;
	}

	n = (char **)malloc(sizeof(char *) * *len);
	if (!n) {
		rc = -1;
		goto bad;
	}

	for (i = 0; i < *len; i++) {
	        n[i] = strdup(namelist[i]->d_name);
		if (!n[i]) {
			rc = -1;
			goto bad_freen;
		}
	}
	rc = 0;
	*names = n;
      out:
	for (i = 0; i < *len; i++) {
		free(namelist[i]);
	}
	free(namelist);
	return rc;
      bad_freen:
	for (--i; i >= 0; --i)
		free(n[i]);
	free(n);
      bad:
	goto out;
}

hidden_def(security_get_boolean_names)
#define STRBUF_SIZE 3
static int get_bool_value(const char *name, char **buf)
{
	int fd, len;
	char *fname = NULL;

	if (!selinux_mnt) {
		errno = ENOENT;
		return -1;
	}

	*buf = (char *)malloc(sizeof(char) * (STRBUF_SIZE + 1));
	if (!*buf)
		goto out;
	(*buf)[STRBUF_SIZE] = 0;

	len = strlen(name) + strlen(selinux_mnt) + sizeof(SELINUX_BOOL_DIR);
	fname = (char *)malloc(sizeof(char) * len);
	if (!fname)
		goto out;
	snprintf(fname, len, "%s%s%s", selinux_mnt, SELINUX_BOOL_DIR, name);

	fd = open(fname, O_RDONLY);
	if (fd < 0)
		goto out;

	len = read(fd, *buf, STRBUF_SIZE);
	close(fd);
	if (len != STRBUF_SIZE)
		goto out;

	free(fname);
	return 0;
      out:
	if (*buf)
		free(*buf);
	if (fname)
		free(fname);
	return -1;
}

int security_get_boolean_pending(const char *name)
{
	char *buf;
	int val;

	if (get_bool_value(name, &buf))
		return -1;

	if (atoi(&buf[1]))
		val = 1;
	else
		val = 0;
	free(buf);
	return val;
}

int security_get_boolean_active(const char *name)
{
	char *buf;
	int val;

	if (get_bool_value(name, &buf))
		return -1;

	buf[1] = '\0';
	if (atoi(buf))
		val = 1;
	else
		val = 0;
	free(buf);
	return val;
}

hidden_def(security_get_boolean_active)

int security_set_boolean(const char *name, int value)
{
	int fd, ret, len;
	char buf[2], *fname;

	if (!selinux_mnt) {
		errno = ENOENT;
		return -1;
	}
	if (value < 0 || value > 1) {
		errno = EINVAL;
		return -1;
	}

	len = strlen(name) + strlen(selinux_mnt) + sizeof(SELINUX_BOOL_DIR);
	fname = (char *)malloc(sizeof(char) * len);
	if (!fname)
		return -1;
	snprintf(fname, len, "%s%s%s", selinux_mnt, SELINUX_BOOL_DIR, name);

	fd = open(fname, O_WRONLY);
	if (fd < 0) {
		ret = -1;
		goto out;
	}

	if (value)
		buf[0] = '1';
	else
		buf[0] = '0';
	buf[1] = '\0';

	ret = write(fd, buf, 2);
	close(fd);
      out:
	free(fname);
	if (ret > 0)
		return 0;
	else
		return -1;
}

hidden_def(security_set_boolean)

int security_commit_booleans(void)
{
	int fd, ret;
	char buf[2];
	char path[PATH_MAX];

	if (!selinux_mnt) {
		errno = ENOENT;
		return -1;
	}

	snprintf(path, sizeof path, "%s/commit_pending_bools", selinux_mnt);
	fd = open(path, O_WRONLY);
	if (fd < 0)
		return -1;

	buf[0] = '1';
	buf[1] = '\0';

	ret = write(fd, buf, 2);
	close(fd);

	if (ret > 0)
		return 0;
	else
		return -1;
}

hidden_def(security_commit_booleans)

static void rollback(SELboolean * boollist, int end)
{
	int i;

	for (i = 0; i < end; i++)
		security_set_boolean(boollist[i].name,
				     security_get_boolean_active(boollist[i].
								 name));
}

int security_set_boolean_list(size_t boolcnt, SELboolean * const boollist,
			      int permanent __attribute__((unused)))
{

	size_t i;
	for (i = 0; i < boolcnt; i++) {
		if (security_set_boolean(boollist[i].name, boollist[i].value)) {
			rollback(boollist, i);
			return -1;
		}
	}

	/* OK, let's do the commit */
	if (security_commit_booleans()) {
		return -1;
	}

	return 0;
}
