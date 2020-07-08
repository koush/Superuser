/*
 * String representation support for classes and permissions.
 */
#include <sys/stat.h>
#include <dirent.h>
#include <fcntl.h>
#include <limits.h>
#include <unistd.h>
#include <errno.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <ctype.h>
#include "selinux_internal.h"
#include "policy.h"
#include "mapping.h"

#define ARRAY_SIZE(x) (sizeof(x) / sizeof((x)[0]))

#define MAXVECTORS 8*sizeof(access_vector_t)

static pthread_once_t once = PTHREAD_ONCE_INIT;

struct discover_class_node {
	char *name;
	security_class_t value;
	char **perms;

	struct discover_class_node *next;
};

static struct discover_class_node *discover_class_cache = NULL;

static struct discover_class_node * get_class_cache_entry_name(const char *s)
{
	struct discover_class_node *node = discover_class_cache;

	for (; node != NULL && strcmp(s,node->name) != 0; node = node->next);

	return node;
}

static struct discover_class_node * get_class_cache_entry_value(security_class_t c)
{
	struct discover_class_node *node = discover_class_cache;

	for (; node != NULL && c != node->value; node = node->next);

	return node;
}

static struct discover_class_node * discover_class(const char *s)
{
	int fd, ret;
	char path[PATH_MAX];
	char buf[20];
	DIR *dir;
	struct dirent *dentry;
	size_t i;

	struct discover_class_node *node;

	if (!selinux_mnt) {
		errno = ENOENT;
		return NULL;
	}

	/* allocate a node */
	node = malloc(sizeof(struct discover_class_node));
	if (node == NULL)
		return NULL;

	/* allocate array for perms */
	node->perms = calloc(MAXVECTORS,sizeof(char*));
	if (node->perms == NULL)
		goto err1;

	/* load up the name */
	node->name = strdup(s);
	if (node->name == NULL)
		goto err2;

	/* load up class index */
	snprintf(path, sizeof path, "%s/class/%s/index", selinux_mnt,s);
	fd = open(path, O_RDONLY);
	if (fd < 0)
		goto err3;

	memset(buf, 0, sizeof(buf));
	ret = read(fd, buf, sizeof(buf) - 1);
	close(fd);
	if (ret < 0)
		goto err3;

	if (sscanf(buf, "%hu", &node->value) != 1)
		goto err3;

	/* load up permission indicies */
	snprintf(path, sizeof path, "%s/class/%s/perms",selinux_mnt,s);
	dir = opendir(path);
	if (dir == NULL)
		goto err3;

	dentry = readdir(dir);
	while (dentry != NULL) {
		unsigned int value;
		struct stat m;

		snprintf(path, sizeof path, "%s/class/%s/perms/%s", selinux_mnt,s,dentry->d_name);
		if (stat(path,&m) < 0)
			goto err4;

		if (m.st_mode & S_IFDIR) {
			dentry = readdir(dir);
			continue;
		}

		fd = open(path, O_RDONLY);
		if (fd < 0)
			goto err4;

		memset(buf, 0, sizeof(buf));
		ret = read(fd, buf, sizeof(buf) - 1);
		close(fd);
		if (ret < 0)
			goto err4;

		if (sscanf(buf, "%u", &value) != 1)
			goto err4;

		node->perms[value-1] = strdup(dentry->d_name);
		if (node->perms[value-1] == NULL)
			goto err4;

		dentry = readdir(dir);
	}
	closedir(dir);

	node->next = discover_class_cache;
	discover_class_cache = node;

	return node;

err4:
	closedir(dir);
	for (i=0; i<MAXVECTORS; i++)
		free(node->perms[i]);
err3:
	free(node->name);
err2:
	free(node->perms);
err1:
	free(node);
	return NULL;
}

void flush_class_cache(void)
{
	struct discover_class_node *cur = discover_class_cache, *prev = NULL;
	size_t i;

	while (cur != NULL) {
		free(cur->name);

		for (i=0 ; i<MAXVECTORS ; i++)
			free(cur->perms[i]);

		free(cur->perms);

		prev = cur;
		cur = cur->next;

		free(prev);
	}

	discover_class_cache = NULL;
}

security_class_t string_to_security_class(const char *s)
{
	struct discover_class_node *node;

	node = get_class_cache_entry_name(s);
	if (node == NULL) {
		node = discover_class(s);

		if (node == NULL) {
			errno = EINVAL;
			return 0;
		}
	}

	return map_class(node->value);
}

access_vector_t string_to_av_perm(security_class_t tclass, const char *s)
{
	struct discover_class_node *node;
	security_class_t kclass = unmap_class(tclass);

	node = get_class_cache_entry_value(kclass);
	if (node != NULL) {
		size_t i;
		for (i=0; i<MAXVECTORS && node->perms[i] != NULL; i++)
			if (strcmp(node->perms[i],s) == 0)
				return map_perm(tclass, 1<<i);
	}

	errno = EINVAL;
	return 0;
}

const char *security_class_to_string(security_class_t tclass)
{
	struct discover_class_node *node;

	tclass = unmap_class(tclass);

	node = get_class_cache_entry_value(tclass);
	if (node)
		return node->name;
	return NULL;
}

const char *security_av_perm_to_string(security_class_t tclass,
				       access_vector_t av)
{
	struct discover_class_node *node;
	size_t i;

	av = unmap_perm(tclass, av);
	tclass = unmap_class(tclass);

	node = get_class_cache_entry_value(tclass);
	if (av && node)
		for (i = 0; i<MAXVECTORS; i++)
			if ((1<<i) & av)
				return node->perms[i];

	return NULL;
}

int security_av_string(security_class_t tclass, access_vector_t av, char **res)
{
	unsigned int i = 0;
	size_t len = 5;
	access_vector_t tmp = av;
	int rc = 0;
	const char *str;
	char *ptr;

	/* first pass computes the required length */
	while (tmp) {
		if (tmp & 1) {
			str = security_av_perm_to_string(tclass, av & (1<<i));
			if (str)
				len += strlen(str) + 1;
			else {
				rc = -1;
				errno = EINVAL;
				goto out;
			}
		}
		tmp >>= 1;
		i++;
	}

	*res = malloc(len);
	if (!*res) {
		rc = -1;
		goto out;
	}

	/* second pass constructs the string */
	i = 0;
	tmp = av;
	ptr = *res;

	if (!av) {
		sprintf(ptr, "null");
		goto out;
	}

	ptr += sprintf(ptr, "{ ");
	while (tmp) {
		if (tmp & 1)
			ptr += sprintf(ptr, "%s ", security_av_perm_to_string(
					       tclass, av & (1<<i)));
		tmp >>= 1;
		i++;
	}
	sprintf(ptr, "}");
out:
	return rc;
}
