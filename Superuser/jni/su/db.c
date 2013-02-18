/*
** Copyright 2013, Koushik Dutta (@koush)
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#include <stdlib.h>
#include <stdio.h>
#include <limits.h>
#include <sqlite3.h>

#include "su.h"

static int database_callback(void *NotUsed, int argc, char **argv, char **azColName){
    int i;
    for(i=0; i<argc; i++){
        printf("%s = %s\n", azColName[i], argv[i] ? argv[i] : "NULL");
    }
    printf("\n");
    return 0;
}

int database_check(struct su_context *ctx) {
    sqlite3 *db = NULL;
    
    char query[512];
    snprintf(query, sizeof(query), "select allow_type, until, command from access where uid=%d", ctx->from.uid);
    int ret = sqlite3_open(query, &db);
    if (ret) {
        LOGE("sqlite3 open failure");
        return ret;
    }

    sqlite3_close(db);

    return -1;
}
