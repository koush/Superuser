package com.koushikdutta.superuser;

import android.app.Application;

public class SuApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        new SuDatabaseHelper(this).getWritableDatabase().close();
    }
}
