package com.koushikdutta.superuser.db;

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.koushikdutta.superuser.UidHelper;

public class SuDatabaseHelper extends SQLiteOpenHelper {
    public SuDatabaseHelper(Context context) {
        super(context, "su.sqlite", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        onUpgrade(db, 0, 1);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 0) {
            db.execSQL("create table if not exists uid_policy (desired_name text, username text, policy text, until integer, command text, uid integer, desired_uid integer, package_name text, name text, primary key(uid, command, desired_uid))");
            db.execSQL("create table if not exists log (id integer primary key autoincrement, desired_name text, username text, uid integer, desired_uid integer, command text, date integer, action text, package_name text, name text)");
            db.execSQL("create index if not exists log_uid_index on log(uid)");
            db.execSQL("create index if not exists log_desired_uid_index on log(desired_uid)");
            db.execSQL("create index if not exists log_command_index on log(command)");
            db.execSQL("create index if not exists log_date_index on log(date)");
            oldVersion = 1;
        }
    }
    
    public static void setPolicy(Context context, UidPolicy policy) {
        SQLiteDatabase db = new SuDatabaseHelper(context).getWritableDatabase();
        
        UidHelper.getPackageInfoForUid(context, policy, false);

        ContentValues values = new ContentValues();
        values.put("uid", policy.uid);
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
    
    private static void getUidCommand(Cursor c, UidCommand u) {
        u.uid = c.getInt(c.getColumnIndex("uid"));
        u.command = c.getString(c.getColumnIndex("command"));
        u.name = c.getString(c.getColumnIndex("name"));
        u.packageName = c.getString(c.getColumnIndex("package_name"));
        u.desiredUid = c.getInt(c.getColumnIndex("desired_uid"));
    }
    
    public static ArrayList<UidPolicy> getPolicies(Context context) {
        ArrayList<UidPolicy> ret = new ArrayList<UidPolicy>();
        SQLiteDatabase db = new SuDatabaseHelper(context).getReadableDatabase();
        Cursor c = db.query("uid_policy", null, null, null, null, null, null);
        try {
            while (c.moveToNext()) {
                UidPolicy u = new UidPolicy();
                ret.add(u);
                getUidCommand(c, u);
                u.policy = c.getString(c.getColumnIndex("policy"));
                u.until = c.getInt(c.getColumnIndex("until"));
                u.icon = UidHelper.loadPackageIcon(context, u.packageName);
                
                ArrayList<LogEntry> logs = getLogs(context, u, 1);
                if (logs.size() > 0)
                    u.last = logs.get(0).date;
            }
        }
        catch (Exception ex) {
        }
        finally {
            c.close();
            db.close();
        }
        return ret;
    }
    
    public static ArrayList<LogEntry> getLogs(Context context, UidPolicy policy, int limit) {
        ArrayList<LogEntry> ret = new ArrayList<LogEntry>();
        SQLiteDatabase db = new SuDatabaseHelper(context).getReadableDatabase();
        Cursor c = db.query("log", null, "uid = ? and desired_uid = ? and command = ?", new String[] { String.valueOf(policy.uid), String.valueOf(policy.desiredUid), policy.command }, null, null, "date DESC", limit == -1 ? null : String.valueOf(limit));
        try {
            while (c.moveToNext()) {
                LogEntry l = new LogEntry();
                ret.add(l);
                getUidCommand(c, l);
                l.id = c.getLong(c.getColumnIndex("id"));
                l.date = c.getInt(c.getColumnIndex("date"));
                l.action = c.getString(c.getColumnIndex("action"));
            }
        }
        catch (Exception ex) {
        }
        finally {
            c.close();
            db.close();
        }
        return ret;
    }
    
    public static ArrayList<LogEntry> getLogs(Context context) {
        ArrayList<LogEntry> ret = new ArrayList<LogEntry>();
        SQLiteDatabase db = new SuDatabaseHelper(context).getReadableDatabase();
        Cursor c = db.query("log", null, null, null, null, null, "date DESC");
        try {
            while (c.moveToNext()) {
                LogEntry l = new LogEntry();
                ret.add(l);
                getUidCommand(c, l);
                l.id = c.getLong(c.getColumnIndex("id"));
                l.date = c.getInt(c.getColumnIndex("date"));
                l.action = c.getString(c.getColumnIndex("action"));
            }
        }
        catch (Exception ex) {
        }
        finally {
            c.close();
            db.close();
        }
        return ret;
    }

    public static void delete(Context context, UidPolicy policy) {
        SQLiteDatabase db = new SuDatabaseHelper(context).getWritableDatabase();
        db.delete("uid_policy", "uid = ? and command = ? and desired_uid = ?", new String[] { String.valueOf(policy.uid), policy.command, String.valueOf(policy.desiredUid) });
        db.close();
    }

    public static void deleteLogs(Context context) {
        SQLiteDatabase db = new SuDatabaseHelper(context).getWritableDatabase();
        db.delete("log", null, null);
        db.close();
    }
    
    public static void addLog(Context context, LogEntry log) {
        SQLiteDatabase db = new SuDatabaseHelper(context).getWritableDatabase();
        
        UidHelper.getPackageInfoForUid(context, log, false);
        
        ContentValues values = new ContentValues();
        values.put("uid", log.uid);
        values.put("command", log.command);
        values.put("action", log.action);
        values.put("date", (int)(System.currentTimeMillis() / 1000));
        values.put("name", log.name);
        values.put("desired_uid", log.desiredUid);
        values.put("package_name", log.packageName);
        values.put("desired_name", log.desiredName);
        values.put("username", log.username);
        db.insert("log", null, values);
        db.close();
    }
    private static final String LOGTAG = "SuReceiver";
}
