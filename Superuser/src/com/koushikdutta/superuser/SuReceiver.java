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

package com.koushikdutta.superuser;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.koushikdutta.superuser.db.LogEntry;
import com.koushikdutta.superuser.db.SuperuserDatabaseHelper;
import com.koushikdutta.superuser.db.UidPolicy;
import com.koushikdutta.superuser.util.Settings;

public class SuReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
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

        final LogEntry le = new LogEntry();
        le.uid = uid;
        le.command = command;
        le.action = action;
        le.desiredUid = desiredUid;
        le.desiredName = desiredName;
        le.username = fromName;
        le.date = (int)(System.currentTimeMillis() / 1000);
        le.getPackageInfo(context);

        UidPolicy u = SuperuserDatabaseHelper.addLog(context, le);

        String toast;
        if (UidPolicy.ALLOW.equals(action)) {
            toast = context.getString(R.string.superuser_granted, le.getName());
        }
        else {
            toast = context.getString(R.string.superuser_denied, le.getName());
        }

        if (u != null && !u.notification)
            return;

        switch (Settings.getNotificationType(context)) {
        case Settings.NOTIFICATION_TYPE_NOTIFICATION:
            Notification.Builder builder = new Notification.Builder(context);
            builder.setTicker(toast)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(), 0))
            .setContentTitle(context.getString(R.string.superuser))
            .setContentText(toast)
            .setSmallIcon(R.drawable.ic_stat_notification);

            NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(NOTIFICATION_ID, builder.build());
            break;
        case Settings.NOTIFICATION_TYPE_TOAST:
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show();
            break;
        }
    }

    private static final int NOTIFICATION_ID = 4545;
}
