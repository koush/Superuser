package com.koushikdutta.superuser;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

public class NotifyActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.notify);
        
        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        int callerUid = intent.getIntExtra("caller_uid", -1);
        if (callerUid == -1) {
            finish();
            return;
        }
        
        final View packageInfo = findViewById(R.id.packageinfo);
        final PackageManager pm = getPackageManager();
        String[] pkgs = pm.getPackagesForUid(callerUid);
        TextView unknown = (TextView)findViewById(R.id.unknown);
        unknown.setText(getString(R.string.unknown_uid, callerUid));

        if (pkgs != null && pkgs.length > 0) {
            for (String pkg: pkgs) {
                try {
                    PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
                    ((TextView)findViewById(R.id.request)).setText(getString(R.string.application_request, pi.applicationInfo.loadLabel(pm)));
                    ImageView icon = (ImageView)packageInfo.findViewById(R.id.image);
                    icon.setImageDrawable(pi.applicationInfo.loadIcon(pm));
                    ((TextView)packageInfo.findViewById(R.id.title)).setText(pi.applicationInfo.loadLabel(pm));
                    
                    ((TextView)findViewById(R.id.app_header)).setText(pi.applicationInfo.loadLabel(pm));
                    ((TextView)findViewById(R.id.package_header)).setText(pi.packageName);
                    
                    // could display them all, but screw it...
                    // maybe a better ux for this later
                    break;
                }
                catch (Exception ex) {
                }
            }
            findViewById(R.id.unknown).setVisibility(View.GONE);
        }
        
        findViewById(R.id.ok).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
