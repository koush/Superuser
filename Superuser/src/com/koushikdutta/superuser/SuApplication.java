package com.koushikdutta.superuser;

import com.koushikdutta.superuser.db.SuDatabaseHelper;

import android.app.Application;

public class SuApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        new SuDatabaseHelper(this).getWritableDatabase().close();
    }
}
