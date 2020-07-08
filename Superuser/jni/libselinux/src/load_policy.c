#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <sys/mount.h>
#include <sys/utsname.h>
#include <fcntl.h>
#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>
#include <string.h>
#include <errno.h>
#include "selinux_internal.h"
#include <dlfcn.h>
#include "policy.h"
#include <limits.h>
#include "callbacks.h"

int security_load_policy(void *data, size_t len)
{
	char path[PATH_MAX];
	int fd, ret;

	if (!selinux_mnt) {
		errno = ENOENT;
		return -1;
	}

	snprintf(path, sizeof path, "%s/load", selinux_mnt);
	fd = open(path, O_RDWR);
	if (fd < 0)
		return -1;

	ret = write(fd, data, len);
	close(fd);
	if (ret < 0)
		return -1;
	return 0;
}

hidden_def(security_load_policy)
