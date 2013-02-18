package com.koushikdutta.superuser;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

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
            db.execSQL("create table if not exists uid_policy (policy text, until integer, command text, uid integer, primary key(uid, command))");
            oldVersion = 1;
        }
    }
    
    public static final String POLICY_ALLOW = "allow";
    public static final String POLICY_DENY = "deny";
    public static final String POLICY_INTERACTOVE = "interactive";
    
    
    public static void setPolicy(Context context, int uid, String command, String policy, int until) {
        SQLiteDatabase db = new SuDatabaseHelper(context).getWritableDatabase();
        
        ContentValues values = new ContentValues();
        values.put("uid", uid);
        values.put("command", command);
        values.put("policy", policy);
        values.put("until", until);
        db.replace("uid_policy", null, values);
    }
}
