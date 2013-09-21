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

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

public class SuDatabaseHelper extends SQLiteOpenHelper {
    private static final int CURRENT_VERSION = 6;
    Context mContext;
    public SuDatabaseHelper(Context context) {
        super(context, "su.sqlite", null, CURRENT_VERSION);
        mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        onUpgrade(db, 0, CURRENT_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 0) {
            db.execSQL("create table if not exists uid_policy (logging integer, desired_name text, username text, policy text, until integer, command text not null, uid integer, desired_uid integer, package_name text, name text, primary key(uid, command, desired_uid))");
            // skip past to v4, as the next migrations have legacy tables, which were moved
            oldVersion = 4;
        }

        if (oldVersion == 1 || oldVersion == 2) {
            db.execSQL("create table if not exists settings (key TEXT PRIMARY KEY, value TEXT)");
            oldVersion = 3;
        }

        // migrate the logs and settings outta this db. fix for db locking issues by su, which
        // only needs a readonly db.
        if (oldVersion == 3) {
            SQLiteDatabase superuser = new SuperuserDatabaseHelper(mContext).getWritableDatabase();

            ArrayList<LogEntry> logs = SuperuserDatabaseHelper.getLogs(mContext, db);
            superuser.beginTransaction();
            try {
                for (LogEntry log: logs) {
                    SuperuserDatabaseHelper.addLog(superuser, log);
                }

                Cursor c = db.query("settings", null, null, null, null, null, null);
                while (c.moveToNext()) {
                    String key = c.getString(c.getColumnIndex("key"));
                    String value = c.getString(c.getColumnIndex("value"));
                    ContentValues cv = new ContentValues();
                    cv.put("key", key);
                    cv.put("value", value);

                    superuser.replace("settings", null, cv);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                superuser.setTransactionSuccessful();
                superuser.endTransaction();
                superuser.close();
            }

            db.execSQL("drop table if exists log");
            db.execSQL("drop table if exists settings");
            oldVersion = 4;
        }

        if (oldVersion == 4) {
            db.execSQL("alter table uid_policy add column notification integer");
            db.execSQL("update uid_policy set notification = 1");
            oldVersion = 5;
        }

        if (oldVersion == 5) {
            // fix bug where null commands are unique from other nulls. eww!
            ArrayList<UidPolicy> policies = getPolicies(db);
            db.delete("uid_policy", null, null);
            for (UidPolicy policy: policies) {
                setPolicy(db, policy);
            }
            oldVersion = 6;
        }
    }
    public static void setPolicy(Context context, UidPolicy policy) {
        policy.getPackageInfo(context);
        SQLiteDatabase db = new SuDatabaseHelper(context).getWritableDatabase();
        try {
            setPolicy(db, policy);
        }
        finally {
            db.close();
        }
    }
    public static void setPolicy(SQLiteDatabase db, UidPolicy policy) {
        ContentValues values = new ContentValues();
        values.put("logging", policy.logging);
        values.put("notification", policy.notification);
        values.put("uid", policy.uid);
        // nulls are considered unique, even from other nulls. blerg.
        // http://stackoverflow.com/questions/3906811/null-permitted-in-primary-key-why-and-in-which-dbms
        if (policy.command == null)
            policy.command = "";
        values.put("command", policy.command);
        values.put("policy", policy.policy);
        values.put("until", policy.until);
        values.put("name", policy.name);
        values.put("package_name", policy.packageName);
        values.put("desired_uid", policy.desiredUid);
        values.put("desired_name", policy.desiredName);
        values.put("username", policy.username);
        db.replace("uid_policy", null, values);
    }

    static UidPolicy getPolicy(Cursor c) {
        UidPolicy u = new UidPolicy();
        u.getUidCommand(c);
        u.policy = c.getString(c.getColumnIndex("policy"));
        u.until = c.getInt(c.getColumnIndex("until"));
        u.logging = c.getInt(c.getColumnIndex("logging")) != 0;
        u.notification = c.getInt(c.getColumnIndex("notification")) != 0;
        return u;
    }


    public static ArrayList<UidPolicy> getPolicies(SQLiteDatabase db) {
        ArrayList<UidPolicy> ret = new ArrayList<UidPolicy>();

        db.delete("uid_policy", "until > 0 and until < ?", new String[] { String.valueOf(System.currentTimeMillis()) });

        Cursor c = db.query("uid_policy", null, null, null, null, null, null);
        try {
            while (c.moveToNext()) {
                UidPolicy u = getPolicy(c);
                ret.add(u);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally {
            c.close();
        }
        return ret;
    }

    public static ArrayList<UidPolicy> getPolicies(Context context) {
        SQLiteDatabase db = new SuDatabaseHelper(context).getWritableDatabase();
        try {
            return getPolicies(db);
        }
        finally {
            db.close();
        }
    }

    public static boolean delete(Context context, UidPolicy policy) {
        SQLiteDatabase db = new SuDatabaseHelper(context).getWritableDatabase();
        if (!TextUtils.isEmpty(policy.command))
            db.delete("uid_policy", "uid = ? and command = ? and desired_uid = ?", new String[] { String.valueOf(policy.uid), policy.command, String.valueOf(policy.desiredUid) });
        else
            db.delete("uid_policy", "uid = ? and desired_uid = ?", new String[] { String.valueOf(policy.uid), String.valueOf(policy.desiredUid) });
        UidPolicy policyEval = get(context, policy.uid, policy.desiredUid, String.valueOf(policy.command));
        db.close();
        return ((policyEval == null) ? true : false);
    }

    public static UidPolicy get(Context context, int uid, int desiredUid, String command) {
        SQLiteDatabase db = new SuDatabaseHelper(context).getReadableDatabase();
        Cursor c;
        if (!TextUtils.isEmpty(command))
            c = db.query("uid_policy", null, "uid = ? and command = ? and desired_uid = ?", new String[] { String.valueOf(uid), command, String.valueOf(desiredUid) }, null, null, null);
        else
            c = db.query("uid_policy", null, "uid = ? and desired_uid = ?", new String[] { String.valueOf(uid), String.valueOf(desiredUid) }, null, null, null);
        try {
            if (c.moveToNext()) {
                return getPolicy(c);
            }
        }
        finally {
            c.close();
            db.close();
        }
        return null;
    }
}
