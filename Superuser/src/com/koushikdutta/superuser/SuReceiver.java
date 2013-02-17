package com.koushikdutta.superuser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SuReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClass(context, MainActivity.class);
        context.startActivity(intent);
    }
}
