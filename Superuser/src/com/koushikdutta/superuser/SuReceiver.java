package com.koushikdutta.superuser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class SuReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle b = intent.getExtras();
        for (String key: b.keySet()) {
            System.out.println(key);
            System.out.println("" + b.getString(key));
            
        }
    }

}
