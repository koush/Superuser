package com.koushikdutta.superuser;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

import junit.framework.Assert;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

public class RequestActivity extends Activity {
    private static final String LOGTAG = "Superuser";
    int mCallerUid;
    int mDesiredUid;
    String mDesiredCmd;
    
    Spinner mSpinner;
    ArrayAdapter<PackageInfo> mAdapter;
    
    Handler mHandler = new Handler();
    
    int mTimeLeft = 3;
    
    Button mAllow;
    Button mDeny;

    boolean mHandled;
    
    public int getGracePeriod() {
        return 10;
    }
    
    void handleAction(boolean action) {
        Assert.assertTrue(!mHandled);
        mHandled = true;
        try {
            mSocket.getOutputStream().write((action ? "socket:ALLOW" : "socket:DENY").getBytes());
        }
        catch (Exception ex) {
        }
        try {
            int until = -1;
            if (mSpinner.isShown()) {
                int pos = mSpinner.getSelectedItemPosition();
                int id = mSpinnerIds[pos];
                if (id == R.string.remember_for) {
                    until = ((int)System.currentTimeMillis() / 1000) + getGracePeriod() * 60;
                }
                else if (id == R.string.remember_forever) {
                    until = 0;
                }
            }
            else if (mRemember.isShown()) {
                if (mRemember.getCheckedRadioButtonId() == R.id.remember_for) {
                    until = ((int)System.currentTimeMillis() / 1000) + getGracePeriod() * 60;
                }
                else if (mRemember.getCheckedRadioButtonId() == R.id.remember_forever) {
                    until = 0;
                }
            }
            if (until != -1)
                SuDatabaseHelper.setPolicy(this, mCallerUid, mDesiredCmd, action ? SuDatabaseHelper.POLICY_ALLOW : SuDatabaseHelper.POLICY_DENY, 0);
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

    boolean mRequestReady;
    void requestReady() {
        findViewById(R.id.incoming).setVisibility(View.GONE);
        findViewById(R.id.ready).setVisibility(View.VISIBLE);
        
        final ListView list = (ListView)findViewById(R.id.list);
        list.setEmptyView(findViewById(R.id.unknown));
        final PackageManager pm = getPackageManager();
        String[] pkgs = pm.getPackagesForUid(mCallerUid);
        TextView unknown = (TextView)findViewById(R.id.unknown);
        unknown.setText(getString(R.string.unknown_uid, mCallerUid));

        final View appInfo = findViewById(R.id.app_info);
        appInfo.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (list.getVisibility() == View.GONE) {
                    appInfo.setVisibility(View.GONE);
                    list.setVisibility(View.VISIBLE);
                }
            }
        });
        
        list.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                if (appInfo.getVisibility() == View.GONE) {
                    appInfo.setVisibility(View.VISIBLE);
                    list.setVisibility(View.GONE);
                }
            }
        });
        
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
        if (pkgs != null) {
            for (String pkg: pkgs) {
                try {
                    PackageInfo pi = pm.getPackageInfo(pkg, 0);
                    ((TextView)findViewById(R.id.request)).setText(getString(R.string.application_request, pi.applicationInfo.loadLabel(pm)));
                    mAdapter.add(pi);
                    ((TextView)findViewById(R.id.app_header)).setText(pi.applicationInfo.loadLabel(pm));
                    ((TextView)findViewById(R.id.package_header)).setText(pi.packageName);
                    
                    // could display them all, but screw it...
                    // maybe a better ux for this later
                    break;
                }
                catch (Exception ex) {
                }
            }
        }
        
        new Runnable() {
            public void run() {
                mAllow.setText(getString(R.string.allow) + " (" + mTimeLeft + ")");
                if (mTimeLeft-- <= 0) {
                    mAllow.setText(getString(R.string.allow));
                    if (!mHandled)
                        mAllow.setEnabled(true);
                    return;
                }
                mHandler.postDelayed(this, 1000);
            };
        }.run();
    }

    void manageSocket() {
        new Thread() {
            @Override
            public void run() {
                try {
                    mSocket = new LocalSocket();
                    mSocket.connect(new LocalSocketAddress(mSocketPath, Namespace.FILESYSTEM));

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
                            mRequestReady = true;
                            requestReady();
                        }
                    });
                    
                    // now, even though the request is ready, keep reading.
                    // if the su process dies for whatever, the socket will close.
                    // in that case, an exception will throw and this activity will finish.
                    is.read();
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
    

    RadioGroup mRemember;
    
    LocalSocket mSocket;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        mSocketPath = intent.getStringExtra("socket");
        if (mSocketPath == null) {
            finish();
            return;
        }
     
        if (getClass() == RequestActivity.class) {
            // TODO: is this the right way to do this?
            // maybe queue requests to maintain the order?
            // fragile apps may have race conditions that occur
            // if the su requests are handled LIFO.

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
        
        setContentView();

        manageSocket();
        
        
        // watch for the socket disappearing. that means su died.
        new Runnable() {
            public void run() {
                if (isFinishing())
                    return;
                if (!new File(mSocketPath).exists()) {
                    finish();
                    return;
                }
                
                mHandler.postDelayed(this, 1000);
            };
        }.run();
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        setContentView();
    }
    
    final int[] mSpinnerIds = new int[] {
            R.string.this_time_only,
            R.string.remember_for,
            R.string.remember_forever
    };
    
    String mSocketPath;
    ArrayAdapter<String> mSpinnerAdapter;
    void setContentView() {
        setContentView(R.layout.request);
        
        ContextThemeWrapper wrapper =  new ContextThemeWrapper(this, R.style.AppDarkTheme);
        LayoutInflater darkInflater = (LayoutInflater)wrapper.getSystemService(LAYOUT_INFLATER_SERVICE);
        ViewGroup root = (ViewGroup)findViewById(R.id.root);
        darkInflater.inflate(R.layout.request_buttons, root);

        mSpinner = (Spinner)findViewById(R.id.remember_choices);
        mSpinner.setAdapter(mSpinnerAdapter = new ArrayAdapter<String>(this, R.layout.request_spinner_choice, R.id.request_spinner_choice));
        for (int id: mSpinnerIds) {
            mSpinnerAdapter.add(getString(id, getGracePeriod()));
        }
        
        mRemember = (RadioGroup)findViewById(R.id.remember);
        RadioButton rememberFor = (RadioButton)findViewById(R.id.remember_for);
        rememberFor.setText(getString(R.string.remember_for, getGracePeriod()));

        mAllow = (Button)findViewById(R.id.allow);
        mDeny = (Button)findViewById(R.id.deny);
        
        mAllow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mAllow.setEnabled(false);
                mDeny.setEnabled(false);
                handleAction(true);
            }
        });
        mDeny.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mAllow.setEnabled(false);
                mDeny.setEnabled(false);
                handleAction(false);
            }
        });
        
        if (mRequestReady)
            requestReady();
    }
}
