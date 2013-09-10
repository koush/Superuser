package com.koushikdutta.superuser;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.koushikdutta.superuser.util.Settings;
import com.koushikdutta.superuser.util.SuHelper;
import com.koushikdutta.superuser.util.exceptions.IllegalBinaryException;
import com.koushikdutta.superuser.util.exceptions.IllegalResultException;

public class SuCheckerReceiver extends BroadcastReceiver {
	private int suUpdateNotificationState = -1;
	
    public static void doNotification(Context context) {
    	NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
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

            //////////////////
            //LiTTle edit
            //////////////////
            final Handler handler = new Handler();
            new Thread() {
                public void run() {
                    try {
                        SuHelper.checkSu(context);
                    }
                    catch(IllegalResultException ire){
                    	handler.post(new Runnable() {
                            @Override
                            public void run() {
                            	doNotification(context);
                            }
                        });
                    }
                    catch (IllegalBinaryException ibe) {
                    	if(ibe.getMessage().equalsIgnoreCase("Su binary is out of date.")){
                    		suUpdateNotificationState = Settings.getInt(context, 
                			Settings.getSuUpdateKey(), 
                			Settings.SU_UPDATE_NOTIFICATION_ON);
                		}
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                            	if(suUpdateNotificationState == Settings.SU_UPDATE_NOTIFICATION_ON)
                            		doNotification(context);
                            }
                        });
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
