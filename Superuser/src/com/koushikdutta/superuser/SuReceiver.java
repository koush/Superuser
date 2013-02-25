package com.koushikdutta.superuser;

import com.koushikdutta.superuser.db.LogEntry;
import com.koushikdutta.superuser.db.SuDatabaseHelper;
import com.koushikdutta.superuser.db.UidPolicy;
import com.koushikdutta.superuser.util.Settings;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

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

        String toast;
        if (UidPolicy.ALLOW.equals(action)) {
            toast = context.getString(R.string.superuser_granted, le.getName());
        }
        else {
            toast = context.getString(R.string.superuser_denied, le.getName());
        }

        switch (Settings.getNotificationType(context)) {
        case Settings.NOTIFICATION_TYPE_NOTIFICATION:
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
            builder.setTicker(toast)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(toast)
            .setSmallIcon(R.drawable.ic_stat_notification);
            
            NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(NOTIFICATION_ID, builder.getNotification());
            break;
        case Settings.NOTIFICATION_TYPE_TOAST:
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show();
            break;
        }
    }
    
    private static final int NOTIFICATION_ID = 4545;
}
