package com.koushikdutta.superuser;

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import com.koushikdutta.superuser.db.SuDatabaseHelper;
import com.koushikdutta.superuser.db.UidPolicy;

public class PackageChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        new Thread() {
            public void run() {
                ArrayList<UidPolicy> policies = SuDatabaseHelper.getPolicies(context);

                if (policies == null)
                    return;

                final PackageManager pm = context.getPackageManager();
                for (UidPolicy policy: policies) {
                    // if the uid did not have a package name at creation time,
                    // it may be a nameless or unresolveable uid...
                    // ie, I can do something like:
                    // su - 5050
                    // # 5050 has no name, so the following su will be an empty package name
                    // su
                    //
                    // ignore this null package name as valid.
                    if (TextUtils.isEmpty(policy.packageName))
                        continue;
                    try {
                        boolean found = false;
                        String[] names = pm.getPackagesForUid(policy.uid);
                        if (names == null)
                            throw new Exception("no packages for uid");
                        for (String name: names) {
                            if (name.equals(policy.packageName))
                                found = true;
                        }
                        if (!found)
                            throw new Exception("no package name match");
                    }
                    catch (Exception e) {
                        SuDatabaseHelper.delete(context, policy);
                    }
                }
            };
        }.start();
    }
}
