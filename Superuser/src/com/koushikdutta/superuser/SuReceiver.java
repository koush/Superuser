package com.koushikdutta.superuser;

import com.koushikdutta.superuser.db.LogEntry;
import com.koushikdutta.superuser.db.SuDatabaseHelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SuReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null)
            return;
        
        String command = intent.getStringExtra("command");
        if (command == null)
            return;
        int uid = intent.getIntExtra("uid", -1);
        if (uid == -1)
            return;
        int desiredUid = intent.getIntExtra("desired_uid", -1);
        if (desiredUid == -1)
            return;
        String action = intent.getStringExtra("action");
        if (action == null)
            return;
        String fromName = intent.getStringExtra("from_name");
        String desiredName = intent.getStringExtra("desired_name");

        LogEntry le = new LogEntry();
        le.uid = uid;
        le.command = command;
        le.action = action;
        le.desiredUid = desiredUid;
        le.desiredName = desiredName;
        le.username = fromName;
        SuDatabaseHelper.addLog(context, le);
    }
}
