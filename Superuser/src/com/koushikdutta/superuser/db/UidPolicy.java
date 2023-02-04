/*
 * Copyright (C) 2013 Koushik Dutta (@koush)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.koushikdutta.superuser.db;

import java.util.Date;

import com.koushikdutta.superuser.R;

public class UidPolicy extends UidCommand {
    public static final String ALLOW = "allow";
    public static final String DENY = "deny";
    public static final String INTERACTIVE = "interactive";

    public String policy;
    public int until;
    public boolean logging = true;
    public boolean notification = true;
    public Date getUntilDate() {
        return new Date((long)until * 1000);
    }

    public int getPolicyResource() {
        if (ALLOW.equals(policy))
            return R.string.allow;
        else if (INTERACTIVE.equals(policy))
            return R.string.interactive;
        return R.string.deny;
    }

    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy){
     this.policy = policy;
    }
}
