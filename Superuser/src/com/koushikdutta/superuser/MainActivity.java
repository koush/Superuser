package com.koushikdutta.superuser;

import java.io.DataInputStream;
import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.LocalSocketAddress.Namespace;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String LOGTAG = "Superuser";
    int mCallerUid;
    int mDesiredUid;
    String mDesiredCmd;
    
    ArrayAdapter<PackageInfo> mAdapter;
    
    Handler mHandler = new Handler();
    
    int mTimeLeft = 3;
    
    Boolean mAction;
    Button mAllow;
    Button mDeny;

    void handleAction() {
        try {
            mSocket.getOutputStream().write((mAction ? "socket:ALLOW" : "socket:DENY").getBytes());
        }
        catch (Exception ex) {
        }
        finish();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mSocket != null)
                mSocket.close();
        }
        catch (Exception ex) {
        }
    }
    
    void requestReady() {
        findViewById(R.id.incoming).setVisibility(View.GONE);
        findViewById(R.id.ready).setVisibility(View.VISIBLE);
        
        ListView list = (ListView)findViewById(R.id.list);
        list.setEnabled(false);
        list.setEmptyView(findViewById(R.id.unknown));
        final PackageManager pm = getPackageManager();
        String[] pkgs = pm.getPackagesForUid(mCallerUid);
        TextView unknown = (TextView)findViewById(R.id.unknown);
        unknown.setText(getString(R.string.unknown_uid, mCallerUid));
        
        ((TextView)findViewById(R.id.uid_header)).setText(Integer.toString(mDesiredUid));
        ((TextView)findViewById(R.id.command_header)).setText(mDesiredCmd);

        mAdapter = new ArrayAdapter<PackageInfo>(this, R.layout.packageinfo, R.id.title) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                convertView = super.getView(position, convertView, parent);
                
                PackageInfo pi = getItem(position);
                ImageView icon = (ImageView)convertView.findViewById(R.id.image);
                icon.setImageDrawable(pi.applicationInfo.loadIcon(pm));
                ((TextView)convertView.findViewById(R.id.title)).setText(pi.applicationInfo.loadLabel(pm));
                
                return convertView;
            }
        };
        
        list.setAdapter(mAdapter);
        for (String pkg: pkgs) {
            try {
                PackageInfo pi = pm.getPackageInfo(pkg, 0);
                ((TextView)findViewById(R.id.request)).setText(getString(R.string.application_request, pi.applicationInfo.loadLabel(pm)));
                mAdapter.add(pi);
                ((TextView)findViewById(R.id.app_header)).setText(pi.applicationInfo.loadLabel(pm));
                ((TextView)findViewById(R.id.package_header)).setText(pi.packageName);
            }
            catch (Exception ex) {
            }
        }
        
        new Runnable() {
            public void run() {
                mAllow.setText(getString(R.string.allow) + " (" + mTimeLeft + ")");
                if (mTimeLeft-- <= 0) {
                    mAllow.setText(getString(R.string.allow));
                    if (mAction == null)
                        mAllow.setEnabled(true);
                    return;
                }
                mHandler.postDelayed(this, 1000);
            };
        }.run();
    }

    void manageSocket(final String socket) {
        new Thread() {
            @Override
            public void run() {
                try {
                    mSocket = new LocalSocket();
                    mSocket.connect(new LocalSocketAddress(socket, Namespace.FILESYSTEM));

                    DataInputStream is = new DataInputStream(mSocket.getInputStream());

                    int protocolVersion = is.readInt();
                    Log.d(LOGTAG, "INT32:PROTO VERSION = " + protocolVersion);

                    int exeSizeMax = is.readInt();
                    Log.d(LOGTAG, "UINT32:FIELD7MAX = " + exeSizeMax);
                    int cmdSizeMax = is.readInt();
                    Log.d(LOGTAG, "UINT32:FIELD9MAX = " + cmdSizeMax);
                    mCallerUid = is.readInt();
                    Log.d(LOGTAG, "UINT32:CALLER = " + mCallerUid);
                    mDesiredUid = is.readInt();
                    Log.d(LOGTAG, "UINT32:TO = " + mDesiredUid);

                    int exeSize = is.readInt();
                    Log.d(LOGTAG, "UINT32:EXESIZE = " + exeSize);
                    if (exeSize > exeSizeMax) {
                        throw new IOException("Incomming string bigger than allowed");
                    }
                    byte[] buf = new byte[exeSize];
                    is.read(buf);
                    String callerBin = new String(buf, 0, exeSize - 1);
                    Log.d(LOGTAG, "STRING:EXE = " + callerBin);

                    int cmdSize = is.readInt();
                    Log.d(LOGTAG, "UINT32:CMDSIZE = " + cmdSize);
                    if (cmdSize > cmdSizeMax) {
                        throw new IOException("Incomming string bigger than allowed");
                    }
                    buf = new byte[cmdSize];
                    is.read(buf);
                    mDesiredCmd = new String(buf, 0, cmdSize - 1);
                    Log.d(LOGTAG, "STRING:CMD = " + mDesiredCmd);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            requestReady();
                        }
                    });
                }
                catch (Exception ex) {
                    Log.i(LOGTAG, ex.getMessage(), ex);
                    try {
                        mSocket.close();
                    }
                    catch (Exception e) {
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    });
                }
            }
        }.start();
    }
    
    LocalSocket mSocket;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        String socket = intent.getStringExtra("socket");
        if (socket == null) {
            finish();
            return;
        }
     
        if (getClass() == MainActivity.class) {
            // MainActivity is actually just a passthrough to a new task
            // stack.
            // Pretty much every superuser implementation i've seen craps out if there
            // is more than 1 su request at a time. Each subsequent su request
            // will get "lost" because there can only be one instance of the activity
            // at a time. Really annoying. This fixes that.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClass(this, MultitaskSuRequestActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        mAllow = (Button)findViewById(R.id.allow);
        mDeny = (Button)findViewById(R.id.deny);
        
        mAllow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mAction = true;
                mAllow.setEnabled(false);
                mDeny.setEnabled(false);
                handleAction();
            }
        });
        mDeny.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mAction = false;
                mAllow.setEnabled(false);
                mDeny.setEnabled(false);
                handleAction();
            }
        });
        manageSocket(socket);
    }
}
