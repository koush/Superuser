#include <unistd.h>
#include <sys/types.h>
#include <fcntl.h>
#include <stdlib.h>
#include <stdio.h>
#include <errno.h>
#include <string.h>
#include <limits.h>
#include "selinux_internal.h"
#include "policy.h"
#include "mapping.h"

int security_compute_create(const char * scon,
				const char * tcon,
				security_class_t tclass,
				char ** newcon)
{
	char path[PATH_MAX];
	char *buf;
	size_t size;
	int fd, ret;

	if (!selinux_mnt) {
		errno = ENOENT;
		return -1;
	}

	snprintf(path, sizeof path, "%s/create", selinux_mnt);
	fd = open(path, O_RDWR);
	if (fd < 0)
		return -1;

	size = selinux_page_size;
	buf = malloc(size);
	if (!buf) {
		ret = -1;
		goto out;
	}
	snprintf(buf, size, "%s %s %hu", scon, tcon, unmap_class(tclass));

	ret = write(fd, buf, strlen(buf));
	if (ret < 0)
		goto out2;

	memset(buf, 0, size);
	ret = read(fd, buf, size - 1);
	if (ret < 0)
		goto out2;

	*newcon = strdup(buf);
	if (!(*newcon)) {
		ret = -1;
		goto out2;
	}
	ret = 0;
      out2:
	free(buf);
      out:
	close(fd);
	return ret;
}
