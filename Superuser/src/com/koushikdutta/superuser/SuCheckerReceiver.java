package com.koushikdutta.superuser;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import com.koushikdutta.superuser.util.Settings;
import com.koushikdutta.superuser.util.SuHelper;

public class SuCheckerReceiver extends BroadcastReceiver {
    public static void doNotification(Context context) {
        Notification.Builder builder = new Notification.Builder(context);
        builder.setTicker(context.getString(R.string.install_superuser));
        builder.setContentTitle(context.getString(R.string.install_superuser));
        builder.setSmallIcon(R.drawable.ic_stat_notification);
        builder.setWhen(0);
        builder.setContentText(context.getString(R.string.su_binary_outdated));
        builder.setAutoCancel(true);
        PendingIntent launch = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), 0);
        Intent delIntent = new Intent(context, SuCheckerReceiver.class);
        delIntent.setAction(ACTION_DELETED);
        PendingIntent delete = PendingIntent.getBroadcast(context, 0, delIntent, 0);
        builder.setDeleteIntent(delete);
        builder.setContentIntent(launch);
        NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(10000, builder.build());
    }

    private static final String ACTION_DELETED = "internal.superuser.ACTION_CHECK_DELETED";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null)
            return;


        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || "internal.superuser.BOOT_TEST".equals(intent.getAction())) {
            // if the user deleted the notification in the past, don't bother them again for a while
            int counter = Settings.getCheckSuQuietCounter(context);
            if (counter > 0) {
                Log.i("Superuser", "Not bothering user... su counter set.");
                counter--;
                Settings.setCheckSuQuietCounter(context, counter);
                return;
            }

            final Handler handler = new Handler();
            new Thread() {
                public void run() {
                    try {
                        SuHelper.checkSu(context);
                    }
                    catch (Exception ex) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                doNotification(context);
                            }
                        });
                    }
                };
            }.start();
        }
        else if (ACTION_DELETED.equals(intent.getAction())) {
            // notification deleted? bother the user in 3 reboots.
            Log.i("Superuser", "Will not bother the user in the future... su counter set.");
            Settings.setCheckSuQuietCounter(context, 3);
        }
    }
}
