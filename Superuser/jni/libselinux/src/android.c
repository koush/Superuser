#include <sys/types.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <ctype.h>
#include <errno.h>
#include <pwd.h>
#include <grp.h>
#include <sys/mman.h>
#include <sys/mount.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/xattr.h>
#include <fcntl.h>
#include <fts.h>
#include <selinux/selinux.h>
#include <selinux/context.h>
#include <selinux/android.h>
#include <selinux/label.h>
#include <selinux/avc.h>
#include <mincrypt/sha.h>
#include <private/android_filesystem_config.h>
#include <log/log.h>
#include "policy.h"
#include "callbacks.h"
#include "selinux_internal.h"
#include "label_internal.h"

/*
 * XXX Where should this configuration file be located?
 * Needs to be accessible by zygote and installd when
 * setting credentials for app processes and setting permissions
 * on app data directories.
 */
static char const * const seapp_contexts_file[] = {
	"/seapp_contexts",
	"/data/security/current/seapp_contexts",
	NULL };

static const struct selinux_opt seopts[] = {
	{ SELABEL_OPT_PATH, "/file_contexts" },
	{ SELABEL_OPT_PATH, "/data/security/current/file_contexts" },
	{ 0, NULL } };

static const char *const sepolicy_file[] = {
	"/sepolicy",
	"/data/security/current/sepolicy",
	NULL };

static const struct selinux_opt seopts_service[] = {
    { SELABEL_OPT_PATH, "/service_contexts" },
    { SELABEL_OPT_PATH, "/data/security/current/service_contexts" },
    { 0, NULL }
};

enum levelFrom {
	LEVELFROM_NONE,
	LEVELFROM_APP,
	LEVELFROM_USER,
	LEVELFROM_ALL
};

#define POLICY_OVERRIDE_VERSION    "/data/security/current/selinux_version"
#define POLICY_BASE_VERSION        "/selinux_version"
static int policy_index = 0;

static void set_policy_index(void)
{
	int fd_base = -1, fd_override = -1;
	struct stat sb_base;
	struct stat sb_override;
	void *map_base, *map_override;

	policy_index = 0;

	fd_base = open(POLICY_BASE_VERSION, O_RDONLY | O_NOFOLLOW);
	if (fd_base < 0)
		return;

	if (fstat(fd_base, &sb_base) < 0) {
		close(fd_base);
		return;
	}

	fd_override = open(POLICY_OVERRIDE_VERSION, O_RDONLY | O_NOFOLLOW);
	if (fd_override < 0) {
		close(fd_base);
		return;
	}

	if (fstat(fd_override, &sb_override) < 0) {
		close(fd_base);
		close(fd_override);
		return;
	}

	if (sb_base.st_size != sb_override.st_size) {
		close(fd_base);
		close(fd_override);
		return;
	}

	map_base = mmap(NULL, sb_base.st_size, PROT_READ, MAP_PRIVATE, fd_base, 0);
	if (map_base == MAP_FAILED) {
		close(fd_base);
		close(fd_override);
		return;
	}

	map_override = mmap(NULL, sb_override.st_size, PROT_READ, MAP_PRIVATE, fd_override, 0);
	if (map_override == MAP_FAILED) {
		munmap(map_base, sb_base.st_size);
		close(fd_base);
		close(fd_override);
		return;
	}

	if (memcmp(map_base, map_override, sb_base.st_size) == 0)
		policy_index = 1;


	close(fd_base);
	close(fd_override);
	munmap(map_base, sb_base.st_size);
	munmap(map_override, sb_override.st_size);
}

bool selinux_android_use_data_policy(void)
{
	set_policy_index();
	return (policy_index == 1);
}

#if DEBUG
static char const * const levelFromName[] = {
	"none",
	"app",
	"user",
	"all"
};
#endif

struct prefix_str {
	size_t len;
	char *str;
	char is_prefix;
};

static void free_prefix_str(struct prefix_str *p)
{
	if (!p)
		return;
	free(p->str);
}

struct seapp_context {
	/* input selectors */
	char isSystemServer;
	struct prefix_str user;
	char *seinfo;
	struct prefix_str name;
	struct prefix_str path;
	/* outputs */
	char *domain;
	char *type;
	char *level;
	char *sebool;
	enum levelFrom levelFrom;
};

static void free_seapp_context(struct seapp_context *s)
{
	if (!s)
		return;

	free_prefix_str(&s->user);
	free(s->seinfo);
	free_prefix_str(&s->name);
	free_prefix_str(&s->path);
	free(s->domain);
	free(s->type);
	free(s->level);
	free(s->sebool);
}

static int seapp_context_cmp(const void *A, const void *B)
{
	const struct seapp_context *const *sp1 = (const struct seapp_context *const *) A;
	const struct seapp_context *const *sp2 = (const struct seapp_context *const *) B;
	const struct seapp_context *s1 = *sp1, *s2 = *sp2;

	/* Give precedence to isSystemServer=true. */
	if (s1->isSystemServer != s2->isSystemServer)
		return (s1->isSystemServer ? -1 : 1);

	/* Give precedence to a specified user= over an unspecified user=. */
	if (s1->user.str && !s2->user.str)
		return -1;
	if (!s1->user.str && s2->user.str)
		return 1;

	if (s1->user.str) {
		/* Give precedence to a fixed user= string over a prefix. */
		if (s1->user.is_prefix != s2->user.is_prefix)
			return (s2->user.is_prefix ? -1 : 1);

		/* Give precedence to a longer prefix over a shorter prefix. */
		if (s1->user.is_prefix && s1->user.len != s2->user.len)
			return (s1->user.len > s2->user.len) ? -1 : 1;
	}

	/* Give precedence to a specified seinfo= over an unspecified seinfo=. */
	if (s1->seinfo && !s2->seinfo)
		return -1;
	if (!s1->seinfo && s2->seinfo)
		return 1;

	/* Give precedence to a specified name= over an unspecified name=. */
	if (s1->name.str && !s2->name.str)
		return -1;
	if (!s1->name.str && s2->name.str)
		return 1;

	if (s1->name.str) {
		/* Give precedence to a fixed name= string over a prefix. */
		if (s1->name.is_prefix != s2->name.is_prefix)
			return (s2->name.is_prefix ? -1 : 1);

		/* Give precedence to a longer prefix over a shorter prefix. */
		if (s1->name.is_prefix && s1->name.len != s2->name.len)
			return (s1->name.len > s2->name.len) ? -1 : 1;
	}

	/* Give precedence to a specified path= over an unspecified path=. */
	if (s1->path.str && !s2->path.str)
		return -1;
	if (!s1->path.str && s2->path.str)
		return 1;

	if (s1->path.str) {
		/* Give precedence to a fixed path= string over a prefix. */
		if (s1->path.is_prefix != s2->path.is_prefix)
			return (s2->path.is_prefix ? -1 : 1);

		/* Give precedence to a longer prefix over a shorter prefix. */
		if (s1->path.is_prefix && s1->path.len != s2->path.len)
			return (s1->path.len > s2->path.len) ? -1 : 1;
	}

        /* Give precedence to a specified sebool= over an unspecified sebool=. */
        if (s1->sebool && !s2->sebool)
                return -1;
        if (!s1->sebool && s2->sebool)
                return 1;

	/* Anything else has equal precedence. */
	return 0;
}

static struct seapp_context **seapp_contexts = NULL;
static int nspec = 0;

static void free_seapp_contexts(void)
{
	int n;

	if (!seapp_contexts)
		return;

	for (n = 0; n < nspec; n++)
		free_seapp_context(seapp_contexts[n]);

	free(seapp_contexts);
	seapp_contexts = NULL;
	nspec = 0;
}

int selinux_android_seapp_context_reload(void)
{
	FILE *fp = NULL;
	char line_buf[BUFSIZ];
	char *token;
	unsigned lineno;
	struct seapp_context *cur;
	char *p, *name = NULL, *value = NULL, *saveptr;
	size_t len;
	int n, ret;

	set_policy_index();

	fp = fopen(seapp_contexts_file[policy_index], "r");
	if (!fp) {
		selinux_log(SELINUX_ERROR, "%s:  could not open any seapp_contexts file", __FUNCTION__);
		return -1;
	}

	free_seapp_contexts();

	nspec = 0;
	while (fgets(line_buf, sizeof line_buf - 1, fp)) {
		p = line_buf;
		while (isspace(*p))
			p++;
		if (*p == '#' || *p == 0)
			continue;
		nspec++;
	}

	seapp_contexts = (struct seapp_context **) calloc(nspec, sizeof(struct seapp_context *));
	if (!seapp_contexts)
		goto oom;

	rewind(fp);
	nspec = 0;
	lineno = 1;
	while (fgets(line_buf, sizeof line_buf - 1, fp)) {
		len = strlen(line_buf);
		if (line_buf[len - 1] == '\n')
			line_buf[len - 1] = 0;
		p = line_buf;
		while (isspace(*p))
			p++;
		if (*p == '#' || *p == 0)
			continue;

		cur = (struct seapp_context *) calloc(1, sizeof(struct seapp_context));
		if (!cur)
			goto oom;

		token = strtok_r(p, " \t", &saveptr);
		if (!token) {
			free_seapp_context(cur);
			goto err;
		}

		while (1) {
			name = token;
			value = strchr(name, '=');
			if (!value) {
				free_seapp_context(cur);
				goto err;
			}
			*value++ = 0;

			if (!strcasecmp(name, "isSystemServer")) {
				if (!strcasecmp(value, "true"))
					cur->isSystemServer = 1;
				else if (!strcasecmp(value, "false"))
					cur->isSystemServer = 0;
				else {
					free_seapp_context(cur);
					goto err;
				}
			} else if (!strcasecmp(name, "user")) {
				cur->user.str = strdup(value);
				if (!cur->user.str) {
					free_seapp_context(cur);
					goto oom;
				}
				cur->user.len = strlen(cur->user.str);
				if (cur->user.str[cur->user.len-1] == '*')
					cur->user.is_prefix = 1;
			} else if (!strcasecmp(name, "seinfo")) {
				cur->seinfo = strdup(value);
				if (!cur->seinfo) {
					free_seapp_context(cur);
					goto oom;
				}
			} else if (!strcasecmp(name, "name")) {
				cur->name.str = strdup(value);
				if (!cur->name.str) {
					free_seapp_context(cur);
					goto oom;
				}
				cur->name.len = strlen(cur->name.str);
				if (cur->name.str[cur->name.len-1] == '*')
					cur->name.is_prefix = 1;
			} else if (!strcasecmp(name, "domain")) {
				cur->domain = strdup(value);
				if (!cur->domain) {
					free_seapp_context(cur);
					goto oom;
				}
			} else if (!strcasecmp(name, "type")) {
				cur->type = strdup(value);
				if (!cur->type) {
					free_seapp_context(cur);
					goto oom;
				}
			} else if (!strcasecmp(name, "levelFromUid")) {
				if (!strcasecmp(value, "true"))
					cur->levelFrom = LEVELFROM_APP;
				else if (!strcasecmp(value, "false"))
					cur->levelFrom = LEVELFROM_NONE;
				else {
					free_seapp_context(cur);
					goto err;
				}
			} else if (!strcasecmp(name, "levelFrom")) {
				if (!strcasecmp(value, "none"))
					cur->levelFrom = LEVELFROM_NONE;
				else if (!strcasecmp(value, "app"))
					cur->levelFrom = LEVELFROM_APP;
				else if (!strcasecmp(value, "user"))
					cur->levelFrom = LEVELFROM_USER;
				else if (!strcasecmp(value, "all"))
					cur->levelFrom = LEVELFROM_ALL;
				else {
					free_seapp_context(cur);
					goto err;
				}
			} else if (!strcasecmp(name, "level")) {
				cur->level = strdup(value);
				if (!cur->level) {
					free_seapp_context(cur);
					goto oom;
				}
			} else if (!strcasecmp(name, "path")) {
				cur->path.str = strdup(value);
				if (!cur->path.str) {
					free_seapp_context(cur);
					goto oom;
				}
				cur->path.len = strlen(cur->path.str);
				if (cur->path.str[cur->path.len-1] == '*')
					cur->path.is_prefix = 1;
			} else if (!strcasecmp(name, "sebool")) {
				cur->sebool = strdup(value);
				if (!cur->sebool) {
					free_seapp_context(cur);
					goto oom;
				}
			} else {
				free_seapp_context(cur);
				goto err;
			}

			token = strtok_r(NULL, " \t", &saveptr);
			if (!token)
				break;
		}

		if (cur->name.str &&
		    (!cur->seinfo || !strcmp(cur->seinfo, "default"))) {
			selinux_log(SELINUX_ERROR, "%s:  No specific seinfo value specified with name=\"%s\", on line %u:  insecure configuration!\n",
				    seapp_contexts_file[policy_index], cur->name.str, lineno);
			free_seapp_context(cur);
			goto err;
		}

		seapp_contexts[nspec] = cur;
		nspec++;
		lineno++;
	}

	qsort(seapp_contexts, nspec, sizeof(struct seapp_context *),
	      seapp_context_cmp);

#if DEBUG
	{
		int i;
		for (i = 0; i < nspec; i++) {
			cur = seapp_contexts[i];
			selinux_log(SELINUX_INFO, "%s:  isSystemServer=%s user=%s seinfo=%s name=%s path=%s sebool=%s -> domain=%s type=%s level=%s levelFrom=%s",
			__FUNCTION__,
			cur->isSystemServer ? "true" : "false", cur->user.str,
			cur->seinfo, cur->name.str, cur->path.str, cur->sebool, cur->domain,
			cur->type, cur->level,
			levelFromName[cur->levelFrom]);
		}
	}
#endif

	ret = 0;

out:
	fclose(fp);
	return ret;

err:
	selinux_log(SELINUX_ERROR, "%s:  Invalid entry on line %u\n",
		    seapp_contexts_file[policy_index], lineno);
	free_seapp_contexts();
	ret = -1;
	goto out;
oom:
	selinux_log(SELINUX_ERROR, 
		    "%s:  Out of memory\n", __FUNCTION__);
	free_seapp_contexts();
	ret = -1;
	goto out;
}


static void seapp_context_init(void)
{
        selinux_android_seapp_context_reload();
}

static pthread_once_t once = PTHREAD_ONCE_INIT;

/*
 * Max id that can be mapped to category set uniquely
 * using the current scheme.
 */
#define CAT_MAPPING_MAX_ID (0x1<<16)

enum seapp_kind {
	SEAPP_TYPE,
	SEAPP_DOMAIN
};

static int seapp_context_lookup(enum seapp_kind kind,
				uid_t uid,
				int isSystemServer,
				const char *seinfo,
				const char *pkgname,
				const char *path,
				context_t ctx)
{
	const char *username = NULL;
	struct seapp_context *cur = NULL;
	int i;
	size_t n;
	uid_t userid;
	uid_t appid;

	__selinux_once(once, seapp_context_init);

	userid = uid / AID_USER;
	appid = uid % AID_USER;
	if (appid < AID_APP) {
		for (n = 0; n < android_id_count; n++) {
			if (android_ids[n].aid == appid) {
				username = android_ids[n].name;
				break;
			}
		}
		if (!username)
			goto err;
	} else if (appid < AID_ISOLATED_START) {
		username = "_app";
		appid -= AID_APP;
	} else {
		username = "_isolated";
		appid -= AID_ISOLATED_START;
	}

	if (appid >= CAT_MAPPING_MAX_ID || userid >= CAT_MAPPING_MAX_ID)
		goto err;

	for (i = 0; i < nspec; i++) {
		cur = seapp_contexts[i];

		if (cur->isSystemServer != isSystemServer)
			continue;

		if (cur->user.str) {
			if (cur->user.is_prefix) {
				if (strncasecmp(username, cur->user.str, cur->user.len-1))
					continue;
			} else {
				if (strcasecmp(username, cur->user.str))
					continue;
			}
		}

		if (cur->seinfo) {
			if (!seinfo || strcasecmp(seinfo, cur->seinfo))
				continue;
		}

		if (cur->name.str) {
			if(!pkgname)
				continue;

			if (cur->name.is_prefix) {
				if (strncasecmp(pkgname, cur->name.str, cur->name.len-1))
					continue;
			} else {
				if (strcasecmp(pkgname, cur->name.str))
					continue;
			}
		}

		if (cur->path.str) {
			if (!path)
				continue;

			if (cur->path.is_prefix) {
				if (strncmp(path, cur->path.str, cur->path.len-1))
					continue;
			} else {
				if (strcmp(path, cur->path.str))
					continue;
			}
		}

		if (kind == SEAPP_TYPE && !cur->type)
			continue;
		else if (kind == SEAPP_DOMAIN && !cur->domain)
			continue;

		if (cur->sebool) {
			int value = security_get_boolean_active(cur->sebool);
			if (value == 0)
				continue;
			else if (value == -1) {
				selinux_log(SELINUX_ERROR, \
				"Could not find boolean: %s ", cur->sebool);
				goto err;
			}
		}

		if (kind == SEAPP_TYPE) {
			if (context_type_set(ctx, cur->type))
				goto oom;
		} else if (kind == SEAPP_DOMAIN) {
			if (context_type_set(ctx, cur->domain))
				goto oom;
		}

		if (cur->levelFrom != LEVELFROM_NONE) {
			char level[255];
			switch (cur->levelFrom) {
			case LEVELFROM_APP:
				snprintf(level, sizeof level, "s0:c%u,c%u",
					 appid & 0xff,
					 256 + (appid>>8 & 0xff));
				break;
			case LEVELFROM_USER:
				snprintf(level, sizeof level, "s0:c%u,c%u",
					 512 + (userid & 0xff),
					 768 + (userid>>8 & 0xff));
				break;
			case LEVELFROM_ALL:
				snprintf(level, sizeof level, "s0:c%u,c%u,c%u,c%u",
					 appid & 0xff,
					 256 + (appid>>8 & 0xff),
					 512 + (userid & 0xff),
					 768 + (userid>>8 & 0xff));
				break;
			default:
				goto err;
			}
			if (context_range_set(ctx, level))
				goto oom;
		} else if (cur->level) {
			if (context_range_set(ctx, cur->level))
				goto oom;
		}

		break;
	}

	if (kind == SEAPP_DOMAIN && i == nspec) {
		/*
		 * No match.
		 * Fail to prevent staying in the zygote's context.
		 */
		selinux_log(SELINUX_ERROR,
			    "%s:  No match for app with uid %d, seinfo %s, name %s\n",
			    __FUNCTION__, uid, seinfo, pkgname);

		if (security_getenforce() == 1)
			goto err;
	}

	return 0;
err:
	return -1;
oom:
	return -2;
}

int selinux_android_setfilecon(const char *pkgdir,
				const char *pkgname,
				const char *seinfo,
				uid_t uid)
{
	char *orig_ctx_str = NULL;
	char *ctx_str = NULL;
	context_t ctx = NULL;
	int rc = -1;

	if (is_selinux_enabled() <= 0)
		return 0;

	rc = getfilecon(pkgdir, &ctx_str);
	if (rc < 0)
		goto err;

	ctx = context_new(ctx_str);
	orig_ctx_str = ctx_str;
	if (!ctx)
		goto oom;

	rc = seapp_context_lookup(SEAPP_TYPE, uid, 0, seinfo, pkgname, NULL, ctx);
	if (rc == -1)
		goto err;
	else if (rc == -2)
		goto oom;

	ctx_str = context_str(ctx);
	if (!ctx_str)
		goto oom;

	rc = security_check_context(ctx_str);
	if (rc < 0)
		goto err;

	if (strcmp(ctx_str, orig_ctx_str)) {
		rc = setfilecon(pkgdir, ctx_str);
		if (rc < 0)
			goto err;
	}

	rc = 0;
out:
	freecon(orig_ctx_str);
	context_free(ctx);
	return rc;
err:
	selinux_log(SELINUX_ERROR, "%s:  Error setting context for pkgdir %s, uid %d: %s\n",
		    __FUNCTION__, pkgdir, uid, strerror(errno));
	rc = -1;
	goto out;
oom:
	selinux_log(SELINUX_ERROR, "%s:  Out of memory\n", __FUNCTION__);
	rc = -1;
	goto out;
}

int selinux_android_setcontext(uid_t uid,
			       int isSystemServer,
			       const char *seinfo,
			       const char *pkgname)
{
	char *orig_ctx_str = NULL, *ctx_str;
	context_t ctx = NULL;
	int rc = -1;

	if (is_selinux_enabled() <= 0)
		return 0;

	rc = getcon(&ctx_str);
	if (rc)
		goto err;

	ctx = context_new(ctx_str);
	orig_ctx_str = ctx_str;
	if (!ctx)
		goto oom;

	rc = seapp_context_lookup(SEAPP_DOMAIN, uid, isSystemServer, seinfo, pkgname, NULL, ctx);
	if (rc == -1)
		goto err;
	else if (rc == -2)
		goto oom;

	ctx_str = context_str(ctx);
	if (!ctx_str)
		goto oom;

	rc = security_check_context(ctx_str);
	if (rc < 0)
		goto err;

	if (strcmp(ctx_str, orig_ctx_str)) {
		rc = setcon(ctx_str);
		if (rc < 0)
			goto err;
	}

	rc = 0;
out:
	freecon(orig_ctx_str);
	context_free(ctx);
	avc_netlink_close();
	return rc;
err:
	if (isSystemServer)
		selinux_log(SELINUX_ERROR,
				"%s:  Error setting context for system server: %s\n",
				__FUNCTION__, strerror(errno));
	else 
		selinux_log(SELINUX_ERROR,
				"%s:  Error setting context for app with uid %d, seinfo %s: %s\n",
				__FUNCTION__, uid, seinfo, strerror(errno));

	rc = -1;
	goto out;
oom:
	selinux_log(SELINUX_ERROR, "%s:  Out of memory\n", __FUNCTION__);
	rc = -1;
	goto out;
}

static struct selabel_handle *sehandle = NULL;
#define FC_DIGEST_SIZE SHA_DIGEST_SIZE
static uint8_t fc_digest[FC_DIGEST_SIZE];

static struct selabel_handle *get_selabel_handle(const struct selinux_opt opts[])
{
    struct selabel_handle *h;
    int fd;
    struct stat sb;
    void *map;

    set_policy_index();

    h = selabel_open(SELABEL_CTX_FILE, &opts[policy_index], 1);
    if (!h)
        return NULL;

    fd = open(opts[policy_index].value, O_RDONLY | O_NOFOLLOW);
    if (fd < 0) {
        selinux_log(SELINUX_ERROR, "SELinux:  Could not open %s:  %s\n",
                    opts[policy_index].value, strerror(errno));
        goto err;
    }
    if (fstat(fd, &sb) < 0) {
        selinux_log(SELINUX_ERROR, "SELinux:  Could not stat %s:  %s\n",
                    opts[policy_index].value, strerror(errno));
        close(fd);
        goto err;
    }
    map = mmap(NULL, sb.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
    if (map == MAP_FAILED) {
        selinux_log(SELINUX_ERROR, "SELinux:  Could not map %s:  %s\n",
                    opts[policy_index].value, strerror(errno));
        close(fd);
        goto err;
    }
    SHA_hash(map, sb.st_size, fc_digest);
    munmap(map, sb.st_size);
    close(fd);

    selinux_log(SELINUX_INFO, "SELinux: Loaded file_contexts from %s\n",
                opts[policy_index].value);

    return h;

err:
    selabel_close(h);
    return NULL;
}

static struct selabel_handle *file_context_open(void)
{
	struct selabel_handle *h;

	h = get_selabel_handle(seopts);

	if (!h)
		selinux_log(SELINUX_ERROR, "%s: Error getting file context handle (%s)\n",
				__FUNCTION__, strerror(errno));
	return h;
}

static void file_context_init(void)
{
    if (!sehandle)
        sehandle = file_context_open();
}

static struct selabel_handle *service_context_open(void)
{
    struct selabel_handle *handle = NULL;

    set_policy_index();
    handle = selabel_open(SELABEL_CTX_ANDROID_PROP,
            &seopts_service[policy_index], 1);

    if (!handle) {
        selinux_log(SELINUX_ERROR, "%s: Error getting service context handle (%s)\n",
                __FUNCTION__, strerror(errno));
    } else {
        selinux_log(SELINUX_INFO, "SELinux: Loaded service contexts from %s.\n",
                seopts_service[policy_index].value);
    }

    return handle;
}

static pthread_once_t fc_once = PTHREAD_ONCE_INIT;

struct pkgInfo {
    char *name;
    uid_t uid;
    bool debuggable;
    char *dataDir;
    char *seinfo;
    struct pkgInfo *next;
};

#define PKGTAB_SIZE 256
static struct pkgInfo *pkgTab[PKGTAB_SIZE];

static unsigned int pkghash(const char *pkgname)
{
    unsigned int h = 7;
    for (; *pkgname; pkgname++) {
        h = h * 31 + *pkgname;
    }
    return h & (PKGTAB_SIZE - 1);
}

/* The file containing the list of installed packages on the system */
#define PACKAGES_LIST_FILE  "/data/system/packages.list"

static void package_info_init(void)
{
    char *buf = NULL;
    size_t buflen = 0;
    ssize_t bytesread;
    FILE *fp;
    char *cur, *next;
    struct pkgInfo *pkgInfo = NULL;
    unsigned int hash;
    unsigned long lineno = 1;

    fp = fopen(PACKAGES_LIST_FILE, "r");
    if (!fp) {
        selinux_log(SELINUX_ERROR, "SELinux:  Could not open %s:  %s.\n",
                    PACKAGES_LIST_FILE, strerror(errno));
        return;
    }
    while ((bytesread = getline(&buf, &buflen, fp)) > 0) {
        pkgInfo = calloc(1, sizeof(*pkgInfo));
        if (!pkgInfo)
            goto err;
        next = buf;
        cur = strsep(&next, " \t\n");
        if (!cur)
            goto err;
        pkgInfo->name = strdup(cur);
        if (!pkgInfo->name)
            goto err;
        cur = strsep(&next, " \t\n");
        if (!cur)
            goto err;
        pkgInfo->uid = atoi(cur);
        if (!pkgInfo->uid)
            goto err;
        cur = strsep(&next, " \t\n");
        if (!cur)
            goto err;
        pkgInfo->debuggable = atoi(cur);
        cur = strsep(&next, " \t\n");
        if (!cur)
            goto err;
        pkgInfo->dataDir = strdup(cur);
        if (!pkgInfo->dataDir)
            goto err;
        cur = strsep(&next, " \t\n");
        if (!cur)
            goto err;
        pkgInfo->seinfo = strdup(cur);
        if (!pkgInfo->seinfo)
            goto err;

        hash = pkghash(pkgInfo->name);
        if (pkgTab[hash])
            pkgInfo->next = pkgTab[hash];
        pkgTab[hash] = pkgInfo;

        lineno++;
    }

#if DEBUG
    {
        unsigned int buckets, entries, chainlen, longestchain;

        buckets = entries = longestchain = 0;
        for (hash = 0; hash < PKGTAB_SIZE; hash++) {
            if (pkgTab[hash]) {
                buckets++;
                chainlen = 0;
                for (pkgInfo = pkgTab[hash]; pkgInfo; pkgInfo = pkgInfo->next) {
                    chainlen++;
                    selinux_log(SELINUX_INFO, "%s:  name=%s uid=%u debuggable=%s dataDir=%s seinfo=%s\n",
                                __FUNCTION__,
                                pkgInfo->name, pkgInfo->uid, pkgInfo->debuggable ? "true" : "false", pkgInfo->dataDir, pkgInfo->seinfo);
                }
                entries += chainlen;
                if (longestchain < chainlen)
                    longestchain = chainlen;
            }
        }
        selinux_log(SELINUX_INFO, "SELinux:  %d pkg entries and %d/%d buckets used, longest chain %d\n", entries, buckets, PKGTAB_SIZE, longestchain);
    }
#endif

out:
    free(buf);
    fclose(fp);
    return;

err:
    selinux_log(SELINUX_ERROR, "SELinux:  Error reading %s on line %lu.\n",
                PACKAGES_LIST_FILE, lineno);
    if (pkgInfo) {
        free(pkgInfo->name);
        free(pkgInfo->dataDir);
        free(pkgInfo->seinfo);
        free(pkgInfo);
    }
    goto out;
}

static pthread_once_t pkg_once = PTHREAD_ONCE_INIT;

struct pkgInfo *package_info_lookup(const char *name)
{
    struct pkgInfo *pkgInfo;
    unsigned int hash;

    __selinux_once(pkg_once, package_info_init);

    hash = pkghash(name);
    for (pkgInfo = pkgTab[hash]; pkgInfo; pkgInfo = pkgInfo->next) {
        if (!strcmp(name, pkgInfo->name))
            return pkgInfo;
    }
    return NULL;
}

/* The path prefixes of package data directories. */
#define DATA_DATA_PATH "/data/data"
#define DATA_USER_PATH "/data/user"
#define DATA_DATA_PREFIX DATA_DATA_PATH "/"
#define DATA_USER_PREFIX DATA_USER_PATH "/"

static int pkgdir_selabel_lookup(const char *pathname,
                                 const char *seinfo,
                                 uid_t uid,
                                 char **secontextp)
{
    char *pkgname = NULL, *end = NULL;
    struct pkgInfo *pkgInfo = NULL;
    char *secontext = *secontextp;
    context_t ctx = NULL;
    int rc = 0;

    /* Skip directory prefix before package name. */
    if (!strncmp(pathname, DATA_DATA_PREFIX, sizeof(DATA_DATA_PREFIX)-1)) {
        pathname += sizeof(DATA_DATA_PREFIX) - 1;
    } else if (!strncmp(pathname, DATA_USER_PREFIX, sizeof(DATA_USER_PREFIX)-1)) {
        pathname += sizeof(DATA_USER_PREFIX) - 1;
        while (isdigit(*pathname))
            pathname++;
        if (*pathname == '/')
            pathname++;
        else
            return 0;
    } else
        return 0;

    if (!(*pathname))
        return 0;

    pkgname = strdup(pathname);
    if (!pkgname)
        return -1;

    for (end = pkgname; *end && *end != '/'; end++)
        ;
    pathname = end;
    if (*end)
        pathname++;
    *end = '\0';

    if (!seinfo) {
        pkgInfo = package_info_lookup(pkgname);
        if (!pkgInfo) {
            selinux_log(SELINUX_WARNING, "SELinux:  Could not look up information for package %s, cannot restorecon %s.\n",
                        pkgname, pathname);
            free(pkgname);
            return -1;
        }
    }

    ctx = context_new(secontext);
    if (!ctx)
        goto err;

    rc = seapp_context_lookup(SEAPP_TYPE, pkgInfo ? pkgInfo->uid : uid, 0,
                              pkgInfo ? pkgInfo->seinfo : seinfo, pkgInfo ? pkgInfo->name : pkgname, pathname, ctx);
    if (rc < 0)
        goto err;

    secontext = context_str(ctx);
    if (!secontext)
        goto err;

    if (!strcmp(secontext, *secontextp))
        goto out;

    rc = security_check_context(secontext);
    if (rc < 0)
        goto err;

    freecon(*secontextp);
    *secontextp = strdup(secontext);
    if (!(*secontextp))
        goto err;

    rc = 0;

out:
    free(pkgname);
    context_free(ctx);
    return rc;
err:
    selinux_log(SELINUX_ERROR, "%s:  Error looking up context for path %s, pkgname %s, seinfo %s, uid %u: %s\n",
                __FUNCTION__, pathname, pkgname, pkgInfo->seinfo, pkgInfo->uid, strerror(errno));
    rc = -1;
    goto out;
}

#define RESTORECON_LAST "security.restorecon_last"

static int restorecon_sb(const char *pathname, const struct stat *sb,
                         bool nochange, bool verbose,
                         const char *seinfo, uid_t uid)
{
    char *secontext = NULL;
    char *oldsecontext = NULL;
    int rc = 0;

    if (selabel_lookup(sehandle, &secontext, pathname, sb->st_mode) < 0)
        return 0;  /* no match, but not an error */

    if (lgetfilecon(pathname, &oldsecontext) < 0)
        goto err;

    /*
     * For subdirectories of /data/data or /data/user, we ignore selabel_lookup()
     * and use pkgdir_selabel_lookup() instead. Files within those directories
     * have different labeling rules, based off of /seapp_contexts, and
     * installd is responsible for managing these labels instead of init.
     */
    if (!strncmp(pathname, DATA_DATA_PREFIX, sizeof(DATA_DATA_PREFIX)-1) ||
        !strncmp(pathname, DATA_USER_PREFIX, sizeof(DATA_USER_PREFIX)-1)) {
        if (pkgdir_selabel_lookup(pathname, seinfo, uid, &secontext) < 0)
            goto err;
    }

    if (strcmp(oldsecontext, secontext) != 0) {
        if (verbose)
            selinux_log(SELINUX_INFO,
                        "SELinux:  Relabeling %s from %s to %s.\n", pathname, oldsecontext, secontext);
        if (!nochange) {
            if (lsetfilecon(pathname, secontext) < 0)
                goto err;
        }
    }

    rc = 0;

out:
    freecon(oldsecontext);
    freecon(secontext);
    return rc;

err:
    selinux_log(SELINUX_ERROR,
                "SELinux: Could not set context for %s:  %s\n",
                pathname, strerror(errno));
    rc = -1;
    goto out;
}

#define SYS_PATH "/sys"
#define SYS_PREFIX SYS_PATH "/"

static int selinux_android_restorecon_common(const char* pathname,
                                             const char *seinfo,
                                             uid_t uid,
                                             unsigned int flags)
{
    bool nochange = (flags & SELINUX_ANDROID_RESTORECON_NOCHANGE) ? true : false;
    bool verbose = (flags & SELINUX_ANDROID_RESTORECON_VERBOSE) ? true : false;
    bool recurse = (flags & SELINUX_ANDROID_RESTORECON_RECURSE) ? true : false;
    bool force = (flags & SELINUX_ANDROID_RESTORECON_FORCE) ? true : false;
    bool datadata = (flags & SELINUX_ANDROID_RESTORECON_DATADATA) ? true : false;
    bool issys = (!strcmp(pathname, SYS_PATH) || !strncmp(pathname, SYS_PREFIX, sizeof(SYS_PREFIX)-1)) ? true : false;
    bool setrestoreconlast = true;
    struct stat sb;
    FTS *fts;
    FTSENT *ftsent;
    char *const paths[2] = { __UNCONST(pathname), NULL };
    int ftsflags = FTS_NOCHDIR | FTS_XDEV | FTS_PHYSICAL;
    int error, sverrno;
    char xattr_value[FC_DIGEST_SIZE];
    ssize_t size;

    if (is_selinux_enabled() <= 0)
        return 0;

    __selinux_once(fc_once, file_context_init);

    if (!sehandle)
        return 0;

    if (!recurse) {
        if (lstat(pathname, &sb) < 0)
            return -1;

        return restorecon_sb(pathname, &sb, nochange, verbose, seinfo, uid);
    }

    /*
     * Ignore restorecon_last on /data/data or /data/user
     * since their labeling is based on seapp_contexts and seinfo
     * assignments rather than file_contexts and is managed by
     * installd rather than init.
     */
    if (!strncmp(pathname, DATA_DATA_PREFIX, sizeof(DATA_DATA_PREFIX)-1) ||
        !strncmp(pathname, DATA_USER_PREFIX, sizeof(DATA_USER_PREFIX)-1))
        setrestoreconlast = false;

    /* Also ignore on /sys since it is regenerated on each boot regardless. */
    if (issys)
        setrestoreconlast = false;

    if (setrestoreconlast) {
        size = getxattr(pathname, RESTORECON_LAST, xattr_value, sizeof fc_digest);
        if (!force && size == sizeof fc_digest && memcmp(fc_digest, xattr_value, sizeof fc_digest) == 0) {
            selinux_log(SELINUX_INFO,
                        "SELinux: Skipping restorecon_recursive(%s)\n",
                        pathname);
            return 0;
        }
    }

    fts = fts_open(paths, ftsflags, NULL);
    if (!fts)
        return -1;

    error = 0;
    while ((ftsent = fts_read(fts)) != NULL) {
        switch (ftsent->fts_info) {
        case FTS_DC:
            selinux_log(SELINUX_ERROR,
                        "SELinux:  Directory cycle on %s.\n", ftsent->fts_path);
            errno = ELOOP;
            error = -1;
            goto out;
        case FTS_DP:
            continue;
        case FTS_DNR:
            selinux_log(SELINUX_ERROR,
                        "SELinux:  Could not read %s: %s.\n", ftsent->fts_path, strerror(errno));
            fts_set(fts, ftsent, FTS_SKIP);
            continue;
        case FTS_NS:
            selinux_log(SELINUX_ERROR,
                        "SELinux:  Could not stat %s: %s.\n", ftsent->fts_path, strerror(errno));
            fts_set(fts, ftsent, FTS_SKIP);
            continue;
        case FTS_ERR:
            selinux_log(SELINUX_ERROR,
                        "SELinux:  Error on %s: %s.\n", ftsent->fts_path, strerror(errno));
            fts_set(fts, ftsent, FTS_SKIP);
            continue;
        case FTS_D:
            if (issys && !selabel_partial_match(sehandle, ftsent->fts_path)) {
                fts_set(fts, ftsent, FTS_SKIP);
                continue;
            }
            if (!datadata &&
                (!strcmp(ftsent->fts_path, DATA_DATA_PATH) ||
                 !strncmp(ftsent->fts_path, DATA_USER_PREFIX, sizeof(DATA_USER_PREFIX)-1))) {
                // Don't label anything below this directory.
                fts_set(fts, ftsent, FTS_SKIP);
                // but fall through and make sure we label the directory itself
            }
            /* fall through */
        default:
            error |= restorecon_sb(ftsent->fts_path, ftsent->fts_statp, nochange, verbose, seinfo, uid);
            break;
        }
    }

    // Labeling successful. Mark the top level directory as completed.
    if (setrestoreconlast && !nochange && !error)
        setxattr(pathname, RESTORECON_LAST, fc_digest, sizeof fc_digest, 0);

out:
    sverrno = errno;
    (void) fts_close(fts);
    errno = sverrno;
    return error;
}

int selinux_android_restorecon(const char *file, unsigned int flags)
{
    return selinux_android_restorecon_common(file, NULL, -1, flags);
}

int selinux_android_restorecon_pkgdir(const char *pkgdir,
                                      const char *seinfo,
                                      uid_t uid,
                                      unsigned int flags)
{
    return selinux_android_restorecon_common(pkgdir, seinfo, uid, flags | SELINUX_ANDROID_RESTORECON_DATADATA);
}

struct selabel_handle* selinux_android_file_context_handle(void)
{
    return file_context_open();
}

struct selabel_handle* selinux_android_service_context_handle(void)
{
    return service_context_open();
}

void selinux_android_set_sehandle(const struct selabel_handle *hndl)
{
    sehandle = (struct selabel_handle *) hndl;
}

static int selinux_android_load_policy_helper(bool reload)
{
	int fd = -1, rc;
	struct stat sb;
	void *map = NULL;

	/*
	 * If reloading policy and there is no /data policy or
	 * that /data policy has the wrong version or the /data
	 * policy is disabled via safe mode, then just return.
	 * There is no point in reloading policy from / a second time.
	 */
	if (reload && !selinux_android_use_data_policy())
		return 0;

	fd = open(sepolicy_file[policy_index], O_RDONLY | O_NOFOLLOW);
	if (fd < 0) {
		selinux_log(SELINUX_ERROR, "SELinux:  Could not open sepolicy:  %s\n",
				strerror(errno));
		return -1;
	}
	if (fstat(fd, &sb) < 0) {
		selinux_log(SELINUX_ERROR, "SELinux:  Could not stat %s:  %s\n",
				sepolicy_file[policy_index], strerror(errno));
		close(fd);
		return -1;
	}
	map = mmap(NULL, sb.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
	if (map == MAP_FAILED) {
		selinux_log(SELINUX_ERROR, "SELinux:  Could not map %s:  %s\n",
			sepolicy_file[policy_index], strerror(errno));
		close(fd);
		return -1;
	}

	rc = security_load_policy(map, sb.st_size);
	if (rc < 0) {
		selinux_log(SELINUX_ERROR, "SELinux:  Could not load policy:  %s\n",
			strerror(errno));
		munmap(map, sb.st_size);
		close(fd);
		return -1;
	}

	munmap(map, sb.st_size);
	close(fd);
	selinux_log(SELINUX_INFO, "SELinux: Loaded policy from %s\n", sepolicy_file[policy_index]);

	return 0;
}

int selinux_android_reload_policy(void)
{
    return selinux_android_load_policy_helper(true);
}

int selinux_android_load_policy(void)
{
	const char *mnt = SELINUXMNT;
	int rc;
	rc = mount(SELINUXFS, mnt, SELINUXFS, 0, NULL);
	if (rc < 0) {
		if (errno == ENODEV) {
			/* SELinux not enabled in kernel */
			return -1;
		}
		if (errno == ENOENT) {
			/* Fall back to legacy mountpoint. */
			mnt = OLDSELINUXMNT;
			rc = mkdir(mnt, 0755);
			if (rc == -1 && errno != EEXIST) {
				selinux_log(SELINUX_ERROR,"SELinux:  Could not mkdir:  %s\n",
					strerror(errno));
				return -1;
			}
			rc = mount(SELINUXFS, mnt, SELINUXFS, 0, NULL);
		}
	}
	if (rc < 0) {
		selinux_log(SELINUX_ERROR,"SELinux:  Could not mount selinuxfs:  %s\n",
				strerror(errno));
		return -1;
	}
	set_selinuxmnt(mnt);

    return selinux_android_load_policy_helper(false);
}

int selinux_log_callback(int type, const char *fmt, ...)
{
    va_list ap;
    int priority;
    char *strp;

    switch(type) {
    case SELINUX_WARNING:
        priority = ANDROID_LOG_WARN;
        break;
    case SELINUX_INFO:
        priority = ANDROID_LOG_INFO;
        break;
    default:
        priority = ANDROID_LOG_ERROR;
        break;
    }

    va_start(ap, fmt);
    if (vasprintf(&strp, fmt, ap) != -1) {
        LOG_PRI(priority, "SELinux", "%s", strp);
        LOG_EVENT_STRING(AUDITD_LOG_TAG, strp);
        free(strp);
    }
    va_end(ap);
    return 0;
}
