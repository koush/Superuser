package com.koushikdutta.superuser;

import java.io.DataInputStream;
import java.io.File;
import java.util.HashMap;

import junit.framework.Assert;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.LocalSocketAddress.Namespace;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
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
import android.widget.Toast;

import com.koushikdutta.superuser.db.SuDatabaseHelper;
import com.koushikdutta.superuser.db.UidPolicy;
import com.koushikdutta.superuser.util.Settings;

public class MultitaskSuRequestActivity extends FragmentActivity {
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
    
    void handleAction(boolean action, boolean user) {
        Assert.assertTrue(!mHandled);
        mHandled = true;
        try {
            mSocket.getOutputStream().write((action ? "socket:ALLOW" : "socket:DENY").getBytes());
        }
        catch (Exception ex) {
        }
        try {
            int until = -1;
            if (user) {
                if (mSpinner.isShown()) {
                    int pos = mSpinner.getSelectedItemPosition();
                    int id = mSpinnerIds[pos];
                    if (id == R.string.remember_for) {
                        until = (int)(System.currentTimeMillis() / 1000) + getGracePeriod() * 60;
                    }
                    else if (id == R.string.remember_forever) {
                        until = 0;
                    }
                }
                else if (mRemember.isShown()) {
                    if (mRemember.getCheckedRadioButtonId() == R.id.remember_for) {
                        until = (int)(System.currentTimeMillis() / 1000) + getGracePeriod() * 60;
                    }
                    else if (mRemember.getCheckedRadioButtonId() == R.id.remember_forever) {
                        until = 0;
                    }
                }
            }
            // got a policy? let's set it.
            if (until != -1) {
                UidPolicy policy = new UidPolicy();
                policy.policy = action ? UidPolicy.ALLOW : UidPolicy.DENY;
                policy.uid = mCallerUid;
                policy.command = mDesiredCmd;
                policy.until = until;
                policy.desiredUid = mDesiredUid;
                SuDatabaseHelper.setPolicy(this, policy);
            }
            // TODO: logging? or should su binary handle that via broadcast?
            // Probably the latter, so it is consolidated and from the system of record.
        }
        catch (Exception ex) {
        }
        finish();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!mHandled)
            handleAction(false, false);
        try {
            if (mSocket != null)
                mSocket.close();
        }
        catch (Exception ex) {
        }
        new File(mSocketPath).delete();
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

    private final static int SU_PROTOCOL_PARAM_MAX = 20;
    private final static int SU_PROTOCOL_NAME_MAX = 20;
    private final static int SU_PROTOCOL_VALUE_MAX_DEFAULT = 256;
    private final static HashMap<String, Integer> SU_PROTOCOL_VALUE_MAX = new HashMap<String, Integer>() {
        {
            put("command", 2048);
        }
    };
    
    private static int getValueMax(String name) {
        Integer max = SU_PROTOCOL_VALUE_MAX.get(name);
        if (max == null)
            return SU_PROTOCOL_VALUE_MAX_DEFAULT;
        return max;
    }
    
    void manageSocket() {
        new Thread() {
            @Override
            public void run() {
                try {
                    mSocket = new LocalSocket();
                    mSocket.connect(new LocalSocketAddress(mSocketPath, Namespace.FILESYSTEM));

                    DataInputStream is = new DataInputStream(mSocket.getInputStream());
                    
                    ContentValues payload = new ContentValues();


                    for (int i = 0; i < SU_PROTOCOL_PARAM_MAX; i++) {
                        int nameLen = is.readInt();
                        if (nameLen > SU_PROTOCOL_NAME_MAX)
                            throw new IllegalArgumentException("name length too long: " + nameLen);
                        byte[] nameBytes = new byte[nameLen];
                        is.readFully(nameBytes);
                        String name = new String(nameBytes);
                        int dataLen = is.readInt();
                        if (dataLen > getValueMax(name))
                            throw new IllegalArgumentException(name + " data length too long: " + dataLen);
                        byte[] dataBytes = new byte[dataLen];
                        is.readFully(dataBytes);
                        String data = new String(dataBytes);
                        payload.put(name, data);
                        Log.i(LOGTAG, name);
                        Log.i(LOGTAG, data);
                        if ("eof".equals(name))
                            break;
                    }
                    
                    int protocolVersion = payload.getAsInteger("version");
                    mCallerUid = payload.getAsInteger("from.uid");
                    mDesiredUid = payload.getAsByte("to.uid");
                    mDesiredCmd = payload.getAsString("command");
                    String calledBin = payload.getAsString("from.bin");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mRequestReady = true;
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
        
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing())
                    return;
                if (!mHandled)
                    handleAction(false, false);
            }
        }, Settings.getRequestTimeout(this) * 1000);
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
    
    void approve() {
        mAllow.setEnabled(false);
        mDeny.setEnabled(false);
        handleAction(true, true);
    }

    void deny() {
        mAllow.setEnabled(false);
        mDeny.setEnabled(false);
        handleAction(false, true);
    }

    String mSocketPath;
    ArrayAdapter<String> mSpinnerAdapter;
    void setContentView() {
        setContentView(R.layout.request);

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
                if (!Settings.isPinProtected(MultitaskSuRequestActivity.this)) {
                    approve();
                    return;
                }
                
                ViewGroup ready = (ViewGroup)findViewById(R.id.root);
                ready.removeAllViews();
                getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.root, new PinFragment() {
                    @Override
                    public void onEnter(String password) {
                        super.onEnter(password);
                        if (Settings.checkPin(getActivity(), password)) {
                            approve();
                        }
                        else {
                            Toast.makeText(getActivity(), getString(R.string.incorrect_pin), Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onCancel() {
                        super.onCancel();
                        deny();
                    }
                })
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();
            }
        });
        mDeny.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                deny();
            }
        });
        
        if (mRequestReady)
            requestReady();
    }
}
