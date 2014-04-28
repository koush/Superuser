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

import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.koushikdutta.superuser.db.SuDatabaseHelper;
import com.koushikdutta.superuser.db.UidPolicy;
import com.koushikdutta.superuser.util.Settings;
import com.koushikdutta.superuser.util.SuHelper;

import junit.framework.Assert;

import java.io.DataInputStream;
import java.io.File;
import java.util.HashMap;

@SuppressLint("ValidFragment")
public class MultitaskSuRequestActivity extends Activity {
    private static final String LOGTAG = "Superuser";
    int mCallerUid;
    int mDesiredUid;
    String mDesiredCmd;
    int mPid;

    Spinner mSpinner;

    Handler mHandler = new Handler();

    int mTimeLeft = 3;

    Button mAllow;
    Button mDeny;

    boolean mHandled;

    public int getGracePeriod() {
        return 10;
    }

    int getUntil() {
        int until = -1;
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
        return until;
    }

    void handleAction(boolean action, Integer until) {
        Assert.assertTrue(!mHandled);
        mHandled = true;
        try {
            mSocket.getOutputStream().write((action ? "socket:ALLOW" : "socket:DENY").getBytes());
        }
        catch (Exception ex) {
        }
        try {
            if (until == null) {
                until = getUntil();
            }
            // got a policy? let's set it.
            if (until != -1) {
                UidPolicy policy = new UidPolicy();
                policy.policy = action ? UidPolicy.ALLOW : UidPolicy.DENY;
                policy.uid = mCallerUid;
                // for now just approve all commands, since per command approval is stupid
//                policy.command = mDesiredCmd;
                policy.command = null;
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
            handleAction(false, -1);
        try {
            if (mSocket != null)
                mSocket.close();
        }
        catch (Exception ex) {
        }
        new File(mSocketPath).delete();
    }

    public static final String PERMISSION = "android.permission.ACCESS_SUPERUSER";

    boolean mRequestReady;
    void requestReady() {
        findViewById(R.id.incoming).setVisibility(View.GONE);
        findViewById(R.id.ready).setVisibility(View.VISIBLE);

        final View packageInfo = findViewById(R.id.packageinfo);
        final PackageManager pm = getPackageManager();
        String[] pkgs = pm.getPackagesForUid(mCallerUid);
        TextView unknown = (TextView)findViewById(R.id.unknown);
        unknown.setText(getString(R.string.unknown_uid, mCallerUid));

        final View appInfo = findViewById(R.id.app_info);
        appInfo.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (packageInfo.getVisibility() == View.GONE) {
                    appInfo.setVisibility(View.GONE);
                    packageInfo.setVisibility(View.VISIBLE);
                }
            }
        });

        packageInfo.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (appInfo.getVisibility() == View.GONE) {
                    appInfo.setVisibility(View.VISIBLE);
                    packageInfo.setVisibility(View.GONE);
                }
            }
        });

        ((TextView)findViewById(R.id.uid_header)).setText(Integer.toString(mDesiredUid));
        ((TextView)findViewById(R.id.command_header)).setText(mDesiredCmd);

        boolean superuserDeclared = false;
        boolean granted = false;
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

                    if (pi.requestedPermissions != null) {
                        for (String perm: pi.requestedPermissions) {
                            if (PERMISSION.equals(perm)) {
                                superuserDeclared = true;
                                break;
                            }
                        }
                    }

                    granted |= checkPermission(PERMISSION, mPid, mCallerUid) == PackageManager.PERMISSION_GRANTED;

                    // could display them all, but screw it...
                    // maybe a better ux for this later
                    break;
                }
                catch (Exception ex) {
                }
            }
            findViewById(R.id.unknown).setVisibility(View.GONE);
        }

        if (!superuserDeclared) {
            findViewById(R.id.developer_warning).setVisibility(View.VISIBLE);
        }

        // handle automatic responses
        // these will be considered permanent user policies
        // even though they are automatic.
        // this is so future su requests dont invoke ui

        // handle declared permission
        if (Settings.getRequirePermission(MultitaskSuRequestActivity.this) && !superuserDeclared) {
            Log.i(LOGTAG, "Automatically denying due to missing permission");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!mHandled)
                        handleAction(false, 0);
                }
            });
            return;
        }

        // automatic response
        switch (Settings.getAutomaticResponse(MultitaskSuRequestActivity.this)) {
        case Settings.AUTOMATIC_RESPONSE_ALLOW:
//            // automatic response and pin can not be used together
//            if (Settings.isPinProtected(MultitaskSuRequestActivity.this))
//                break;
            // check if the permission must be granted
            if (Settings.getRequirePermission(MultitaskSuRequestActivity.this) && !granted)
                break;
            Log.i(LOGTAG, "Automatically allowing due to user preference");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!mHandled)
                        handleAction(true, 0);
                }
            });
            return;
        case Settings.AUTOMATIC_RESPONSE_DENY:
            Log.i(LOGTAG, "Automatically denying due to user preference");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!mHandled)
                        handleAction(false, 0);
                }
            });
            return;
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
        /**
         *
         */
 private static final long serialVersionUID = 5649873127008413475L;

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
//                        Log.i(LOGTAG, name);
//                        Log.i(LOGTAG, data);
                        if ("eof".equals(name))
                            break;
                    }

                    //int protocolVersion = payload.getAsInteger("version");
                    mCallerUid = payload.getAsInteger("from.uid");
                    mDesiredUid = payload.getAsByte("to.uid");
                    mDesiredCmd = payload.getAsString("command");
                    //String calledBin = payload.getAsString("from.bin");
                    mPid = payload.getAsInteger("pid");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mRequestReady = true;
                            requestReady();
                        }
                    });

                    if ("com.koushikdutta.superuser".equals(getPackageName())) {
                        if (!SuHelper.CURRENT_VERSION.equals(payload.getAsString("binary.version")))
                            SuCheckerReceiver.doNotification(MultitaskSuRequestActivity.this);
                    }
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
        Settings.applyDarkThemeSetting(this, R.style.RequestThemeDark);
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
                    handleAction(false, -1);
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
        handleAction(true, null);
    }

    void deny() {
        mAllow.setEnabled(false);
        mDeny.setEnabled(false);
        handleAction(false, null);
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
                final int until = getUntil();
                ready.removeAllViews();

                PinViewHelper pin = new PinViewHelper(getLayoutInflater(), (ViewGroup)findViewById(android.R.id.content), null) {
                    @Override
                    public void onEnter(String password) {
                        super.onEnter(password);
                        if (Settings.checkPin(MultitaskSuRequestActivity.this, password)) {
                            mAllow.setEnabled(false);
                            mDeny.setEnabled(false);
                            handleAction(true, until);
                        }
                        else {
                            Toast.makeText(MultitaskSuRequestActivity.this, getString(R.string.incorrect_pin), Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onCancel() {
                        super.onCancel();
                        deny();
                    }
                };

                ready.addView(pin.getView());
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
