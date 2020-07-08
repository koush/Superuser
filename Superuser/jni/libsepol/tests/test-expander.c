/*
 * Authors: Chad Sellers <csellers@tresys.com>
 *          Joshua Brindle <jbrindle@tresys.com>
 *
 * Copyright (C) 2006 Tresys Technology, LLC
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

/* This is where the expander tests should go, including:
 * - check role, type, bool, user mapping
 * - add symbols declared in enabled optionals
 * - do not add symbols declared in disabled optionals
 * - add rules from enabled optionals
 * - do not add rules from disabled optionals
 * - verify attribute mapping

 * - check conditional expressions for correct mapping
 */

#include "test-expander.h"
#include "parse_util.h"
#include "helpers.h"
#include "test-common.h"
#include "test-expander-users.h"
#include "test-expander-roles.h"
#include "test-expander-attr-map.h"

#include <sepol/policydb/policydb.h>
#include <sepol/policydb/expand.h>
#include <sepol/policydb/link.h>
#include <sepol/policydb/conditional.h>
#include <limits.h>
#include <stdlib.h>

policydb_t role_expanded;
policydb_t user_expanded;
policydb_t base_expanded2;
static policydb_t basemod;
static policydb_t basemod2;
static policydb_t mod2;
static policydb_t base_expanded;
static policydb_t base_only_mod;
static policydb_t base_only_expanded;
static policydb_t role_basemod;
static policydb_t role_mod;
static policydb_t user_basemod;
static policydb_t user_mod;
static policydb_t alias_basemod;
static policydb_t alias_mod;
static policydb_t alias_expanded;
static uint32_t *typemap;
extern int mls;

/* Takes base, some number of modules, links them, and expands them
   reads source from myfiles array, which has the base string followed by
   each module string */
int expander_policy_init(policydb_t * mybase, int num_modules, policydb_t ** mymodules, policydb_t * myexpanded, char **myfiles)
{
	char *filename[num_modules + 1];
	int i;

	for (i = 0; i < num_modules + 1; i++) {
		filename[i] = calloc(PATH_MAX, sizeof(char));
		if (snprintf(filename[i], PATH_MAX, "policies/test-expander/%s%s", myfiles[i], mls ? ".mls" : ".std") < 0)
			return -1;
	}

	if (policydb_init(mybase)) {
		fprintf(stderr, "out of memory!\n");
		return -1;
	}

	for (i = 0; i < num_modules; i++) {
		if (policydb_init(mymodules[i])) {
			fprintf(stderr, "out of memory!\n");
			return -1;
		}
	}

	if (policydb_init(myexpanded)) {
		fprintf(stderr, "out of memory!\n");
		return -1;
	}

	mybase->policy_type = POLICY_BASE;
	mybase->mls = mls;

	if (read_source_policy(mybase, filename[0], myfiles[0])) {
		fprintf(stderr, "read source policy failed %s\n", filename[0]);
		return -1;
	}

	for (i = 1; i < num_modules + 1; i++) {
		mymodules[i - 1]->policy_type = POLICY_MOD;
		mymodules[i - 1]->mls = mls;
		if (read_source_policy(mymodules[i - 1], filename[i], myfiles[i])) {
			fprintf(stderr, "read source policy failed %s\n", filename[i]);
			return -1;
		}
	}

	if (link_modules(NULL, mybase, mymodules, num_modules, 0)) {
		fprintf(stderr, "link modules failed\n");
		return -1;
	}

	if (expand_module(NULL, mybase, myexpanded, 0, 0)) {
		fprintf(stderr, "expand modules failed\n");
		return -1;
	}

	return 0;
}

int expander_test_init(void)
{
	char *small_base_file = "small-base.conf";
	char *base_only_file = "base-base-only.conf";
	int rc;
	policydb_t *mymod2;
	char *files2[] = { "small-base.conf", "module.conf" };
	char *role_files[] = { "role-base.conf", "role-module.conf" };
	char *user_files[] = { "user-base.conf", "user-module.conf" };
	char *alias_files[] = { "alias-base.conf", "alias-module.conf" };

	rc = expander_policy_init(&basemod, 0, NULL, &base_expanded, &small_base_file);
	if (rc != 0)
		return rc;

	mymod2 = &mod2;
	rc = expander_policy_init(&basemod2, 1, &mymod2, &base_expanded2, files2);
	if (rc != 0)
		return rc;

	rc = expander_policy_init(&base_only_mod, 0, NULL, &base_only_expanded, &base_only_file);
	if (rc != 0)
		return rc;

	mymod2 = &role_mod;
	rc = expander_policy_init(&role_basemod, 1, &mymod2, &role_expanded, role_files);
	if (rc != 0)
		return rc;

	/* Just init the base for now, until we figure out how to separate out
	   mls and non-mls tests since users can't be used in mls module */
	mymod2 = &user_mod;
	rc = expander_policy_init(&user_basemod, 0, NULL, &user_expanded, user_files);
	if (rc != 0)
		return rc;

	mymod2 = &alias_mod;
	rc = expander_policy_init(&alias_basemod, 1, &mymod2, &alias_expanded, alias_files);
	if (rc != 0)
		return rc;

	return 0;
}

int expander_test_cleanup(void)
{
	policydb_destroy(&basemod);
	policydb_destroy(&base_expanded);
	free(typemap);

	return 0;
}

static void test_expander_indexes(void)
{
	test_policydb_indexes(&base_expanded);
}

static void test_expander_alias(void)
{
	test_alias_datum(&alias_expanded, "alias_check_1_a", "alias_check_1_t", 1, 0);
	test_alias_datum(&alias_expanded, "alias_check_2_a", "alias_check_2_t", 1, 0);
	test_alias_datum(&alias_expanded, "alias_check_3_a", "alias_check_3_t", 1, 0);
}

int expander_add_tests(CU_pSuite suite)
{
	if (NULL == CU_add_test(suite, "expander_indexes", test_expander_indexes)) {
		CU_cleanup_registry();
		return CU_get_error();
	}

	if (NULL == CU_add_test(suite, "expander_attr_mapping", test_expander_attr_mapping)) {
		CU_cleanup_registry();
		return CU_get_error();
	}

	if (NULL == CU_add_test(suite, "expander_role_mapping", test_expander_role_mapping)) {
		CU_cleanup_registry();
		return CU_get_error();
	}
	if (NULL == CU_add_test(suite, "expander_user_mapping", test_expander_user_mapping)) {
		CU_cleanup_registry();
		return CU_get_error();
	}
	if (NULL == CU_add_test(suite, "expander_alias", test_expander_alias)) {
		CU_cleanup_registry();
		return CU_get_error();
	}
	return 0;
}
