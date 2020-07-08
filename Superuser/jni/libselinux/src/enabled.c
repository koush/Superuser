#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include "selinux_internal.h"
#include <stdlib.h>
#include <errno.h>
#include <limits.h>
#include <stdio.h>
#include "policy.h"

int is_selinux_enabled(void)
{
	char buf[BUFSIZ];
	FILE *fp;
	char *bufp;
	int enabled = 0;
	char * con;

	/* init_selinuxmnt() gets called before this function. We
 	 * will assume that if a selinux file system is mounted, then
 	 * selinux is enabled. */
	if (selinux_mnt) {

		/* Since a file system is mounted, we consider selinux
		 * enabled. If getcon fails, selinux is still enabled.
		 * We only consider it disabled if no policy is loaded. */
		enabled = 1;
		if (getcon(&con) == 0) {
			if (!strcmp(con, "kernel"))
				enabled = 0;
			freecon(con);
		}
		return enabled;
        }

	/* Drop back to detecting it the long way. */
	fp = fopen("/proc/filesystems", "r");
	if (!fp)
		return -1;

	while ((bufp = fgets(buf, sizeof buf - 1, fp)) != NULL) {
		if (strstr(buf, "selinuxfs")) {
			enabled = 1;
			break;
		}
	}

	if (!bufp)
		goto out;

	/* Since an selinux file system is available, we consider
	 * selinux enabled. If getcon fails, selinux is still
	 * enabled. We only consider it disabled if no policy is loaded. */
	if (getcon(&con) == 0) {
		if (!strcmp(con, "kernel"))
			enabled = 0;
		freecon(con);
	}

      out:
	fclose(fp);
	return enabled;
}

hidden_def(is_selinux_enabled)

/*
 * Function: is_selinux_mls_enabled()
 * Return:   1 on success
 *	     0 on failure
 */
int is_selinux_mls_enabled(void)
{
	char buf[20], path[PATH_MAX];
	int fd, ret, enabled = 0;

	if (!selinux_mnt)
		return enabled;

	snprintf(path, sizeof path, "%s/mls", selinux_mnt);
	fd = open(path, O_RDONLY);
	if (fd < 0)
		return enabled;

	memset(buf, 0, sizeof buf);

	do {
		ret = read(fd, buf, sizeof buf - 1);
	} while (ret < 0 && errno == EINTR);
	close(fd);
	if (ret < 0)
		return enabled;

	if (!strcmp(buf, "1"))
		enabled = 1;

	return enabled;
}

hidden_def(is_selinux_mls_enabled)
