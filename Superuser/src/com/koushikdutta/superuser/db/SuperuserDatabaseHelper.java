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

import com.koushikdutta.superuser.util.Settings;

public class SuperuserDatabaseHelper extends SQLiteOpenHelper {
    private static final int CURRENT_VERSION = 1;
    public SuperuserDatabaseHelper(Context context) {
        super(context, "superuser.sqlite", null, CURRENT_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        onUpgrade(db, 0, CURRENT_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 0) {
            db.execSQL("create table if not exists log (id integer primary key autoincrement, desired_name text, username text, uid integer, desired_uid integer, command text, date integer, action text, package_name text, name text)");
            db.execSQL("create index if not exists log_uid_index on log(uid)");
            db.execSQL("create index if not exists log_desired_uid_index on log(desired_uid)");
            db.execSQL("create index if not exists log_command_index on log(command)");
            db.execSQL("create index if not exists log_date_index on log(date)");
            db.execSQL("create table if not exists settings (key TEXT PRIMARY KEY, value TEXT)");
            oldVersion = 1;
        }
    }
    
    public static ArrayList<LogEntry> getLogs(Context context, UidPolicy policy, int limit) {
        SQLiteDatabase db = new SuperuserDatabaseHelper(context).getReadableDatabase();
        try {
            return getLogs(db, policy, limit);
        }
        finally {
            db.close();
        }
    }    
    public static ArrayList<LogEntry> getLogs(SQLiteDatabase db, UidPolicy policy, int limit) {
        ArrayList<LogEntry> ret = new ArrayList<LogEntry>();
        Cursor c;
        if (policy.command != null)
            c = db.query("log", null, "uid = ? and desired_uid = ? and command = ?", new String[] { String.valueOf(policy.uid), String.valueOf(policy.desiredUid), policy.command }, null, null, "date DESC", limit == -1 ? null : String.valueOf(limit));
        else
            c = db.query("log", null, "uid = ? and desired_uid = ?", new String[] { String.valueOf(policy.uid), String.valueOf(policy.desiredUid) }, null, null, "date DESC", limit == -1 ? null : String.valueOf(limit));
        try {
            while (c.moveToNext()) {
                LogEntry l = new LogEntry();
                ret.add(l);
                l.getUidCommand(c);
                l.id = c.getLong(c.getColumnIndex("id"));
                l.date = c.getInt(c.getColumnIndex("date"));
                l.action = c.getString(c.getColumnIndex("action"));
            }
        }
        catch (Exception ex) {
        }
        finally {
            c.close();
        }
        return ret;
    }
    
    public static ArrayList<LogEntry> getLogs(Context context) {
        SQLiteDatabase db = new SuperuserDatabaseHelper(context).getReadableDatabase();
        try {
            return getLogs(context, db);
        }
        finally {
            db.close();
        }
    }    
    public static ArrayList<LogEntry> getLogs(Context context, SQLiteDatabase db) {
        ArrayList<LogEntry> ret = new ArrayList<LogEntry>();
        Cursor c = db.query("log", null, null, null, null, null, "date DESC");
        try {
            while (c.moveToNext()) {
                LogEntry l = new LogEntry();
                ret.add(l);
                l.getUidCommand(c);
                l.id = c.getLong(c.getColumnIndex("id"));
                l.date = c.getInt(c.getColumnIndex("date"));
                l.action = c.getString(c.getColumnIndex("action"));
            }
        }
        catch (Exception ex) {
        }
        finally {
            c.close();
        }
        return ret;
    }

    public static void deleteLogs(Context context) {
        SQLiteDatabase db = new SuperuserDatabaseHelper(context).getWritableDatabase();
        db.delete("log", null, null);
        db.close();
    }
    
    static void addLog(SQLiteDatabase db, LogEntry log) {
        ContentValues values = new ContentValues();
        values.put("uid", log.uid);
        values.put("command", log.command);
        values.put("action", log.action);
        values.put("date", log.date);
        values.put("name", log.name);
        values.put("desired_uid", log.desiredUid);
        values.put("package_name", log.packageName);
        values.put("desired_name", log.desiredName);
        values.put("username", log.username);
        db.insert("log", null, values);
    }
    
    public static void addLog(Context context, LogEntry log) {
        if (!Settings.getLogging(context))
            return;
        
        SQLiteDatabase db = new SuDatabaseHelper(context).getReadableDatabase();
        Cursor c = db.query("uid_policy", null, "uid = ? and command = ? and desired_uid = ?", new String[] { String.valueOf(log.uid), log.command, String.valueOf(log.desiredUid) }, null, null, null, null);
        try {
            if (c.moveToNext()) {
                UidPolicy u = SuDatabaseHelper.getPolicy(context, c);
                if (!u.logging) {
                    db.close();
                    return;
                }
            }
        }
        finally {
            c.close();
            db.close();
        }

        db = new SuperuserDatabaseHelper(context).getReadableDatabase();
        try {
            addLog(db, log);
        }
        finally {
            db.close();
        }
    }
}
