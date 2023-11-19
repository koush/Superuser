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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;


public class UidCommand {
    public String username;
    public String name;
    public String packageName;
    public int uid;
    public String command;
    public int desiredUid;
    public String desiredName;

    public String getName() {
        if (name != null)
            return name;
        if (packageName != null)
            return packageName;
        if (username != null && username.length() > 0)
            return username;
        return String.valueOf(uid);
    }


    public void getPackageInfo(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = context.getPackageManager().getPackageInfo(pm.getPackagesForUid(uid)[0], 0);
            name = pi.applicationInfo.loadLabel(pm).toString();
            packageName = pi.packageName;
        }
        catch (Exception ex) {
        }
    }

    public void getUidCommand(Cursor c) {
        uid = c.getInt(c.getColumnIndex("uid"));
        command = c.getString(c.getColumnIndex("command"));
        name = c.getString(c.getColumnIndex("name"));
        packageName = c.getString(c.getColumnIndex("package_name"));
        desiredUid = c.getInt(c.getColumnIndex("desired_uid"));
        desiredName = c.getString(c.getColumnIndex("desired_name"));
        username = c.getString(c.getColumnIndex("username"));
    }
}
