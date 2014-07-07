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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.superuser.db.LogEntry;
import com.koushikdutta.superuser.db.SuDatabaseHelper;
import com.koushikdutta.superuser.db.SuperuserDatabaseHelper;
import com.koushikdutta.superuser.db.UidPolicy;
import com.koushikdutta.widgets.ListContentFragment;
import com.koushikdutta.widgets.ListItem;

import java.util.ArrayList;
import java.util.Date;

public class PolicyFragment extends ListContentFragment {

    private static final String DATA_BUNDLE_KEY = "deleted";

    ContextThemeWrapper mWrapper;

    @Override
    public Context getContext() {
        if (mWrapper != null)
            return mWrapper;
        TypedValue value = new TypedValue();
        super.getContext().getTheme().resolveAttribute(R.attr.largeIconTheme, value, true);
        mWrapper = new ContextThemeWrapper(super.getContext(), value.resourceId);
        return mWrapper;
    }

    void showAllLogs() {
        setContent(null, null);
        getListView().clearChoices();
    }

    void load() {
        clear();
        final ArrayList<UidPolicy> policies = SuDatabaseHelper.getPolicies(getActivity());

        SQLiteDatabase db = new SuperuserDatabaseHelper(getActivity()).getReadableDatabase();
        try {
            for (UidPolicy up : policies) {
                int last = 0;
                ArrayList<LogEntry> logs = SuperuserDatabaseHelper.getLogs(db, up, 1);
                if (logs.size() > 0)
                    last = logs.get(0).date;
                addPolicy(up, last);
            }
        } finally {
            db.close();
        }
    }

    public void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void onCreate(Bundle savedInstanceState, View view) {
        super.onCreate(savedInstanceState, view);

        setHasOptionsMenu(true);

        TextView empty = (TextView)getLayoutInflater(savedInstanceState).inflate(R.layout.empty, null);
        ((ViewGroup)view.findViewById(R.id.empty)).addView(empty);
        empty.setText(R.string.no_apps);

        load();

        if ("com.koushikdutta.superuser".equals(getContext().getPackageName())) {
            ImageView watermark = (ImageView) view.findViewById(R.id.watermark);
            if (watermark != null)
                watermark.setImageResource(R.drawable.clockwork512);
        }
        if (!isPaged())
            showAllLogs();
    }

    public Date getLastDate(int last) {
        return new Date((long) last * 1000);
    }

    void addPolicy(final UidPolicy up, final int last) {
        java.text.DateFormat df = DateFormat.getLongDateFormat(getActivity());
        String date;
        if (last == 0)
            date = null;
        else
            date = df.format(getLastDate(last));
        ListItem li = addItem(up.getPolicyResource(), new ListItem(this, up.name, date) {
            public void onClick(View view) {
                super.onClick(view);

                setContent(this, up);
            }

            ;

            @Override
            public boolean onLongClick() {
                showExtraActions(up, this);
                return true;
            }
        });

        Drawable icon = Helper.loadPackageIcon(getActivity(), up.packageName);
        if (icon == null)
            li.setIcon(R.drawable.ic_launcher);
        else
            li.setDrawable(icon);
    }

    public void showExtraActions(final UidPolicy up, final ListItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(up.name);
        builder.setIcon(Helper.loadPackageIcon(getActivity(), up.packageName));
        final String permissionChange = (up.policy.equalsIgnoreCase(UidPolicy.ALLOW)) ?
        getResources().getText(R.string.deny).toString() :
        getResources().getText(R.string.allow).toString();
        String[] items = new String[]{permissionChange, getString(R.string.revoke_permission),
        getString(R.string.details)};
        builder.setItems(items, new OnClickListener() {
            @SuppressLint("HandlerLeak")
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        if (permissionChange.equalsIgnoreCase(
                        getResources().getText(R.string.allow).toString())) {
                            up.setPolicy(UidPolicy.ALLOW);
                        } else {
                            up.setPolicy(UidPolicy.DENY);
                        }
                        SuDatabaseHelper.setPolicy(getActivity(), up);
                        //update the adapters
                        load();
                        break;
                    case 1:
                        final Handler handler = new Handler() {
                            @Override
                            public void handleMessage(Message msg) {
                                // TODO Auto-generated method stub
                                if (msg.getData().getBoolean(DATA_BUNDLE_KEY))
                                    removeItem(item);
                                else
                                    showErrorDialog(up, R.string.db_delete_error);
                            }
                        };
                        new Thread() {
                            public void run() {
                                final boolean done = SuDatabaseHelper.delete(getActivity(), up);
                                Message msg = handler.obtainMessage();
                                Bundle bundle = new Bundle();
                                bundle.putBoolean(DATA_BUNDLE_KEY, done);
                                msg.setData(bundle);
                                handler.sendMessage(msg);
                            }
                        }.start();
                        //dismiss the actions' dialog
                        dialog.dismiss();
                        break;
                    case 2:
                        setContent(item, up);
                        dialog.dismiss();
                        break;
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showErrorDialog(UidPolicy policy, int resource) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
        .setTitle(policy.name)
        .setIcon(Helper.loadPackageIcon(getActivity(), policy.packageName))
        .setMessage(getResources().getText(resource))
        .setCancelable(true)
        .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    void setContent(final ListItem li, final UidPolicy up) {
        LogFragment l = createLogFragment();
        l.setUidPolicy(up);
        if (up != null) {
            Bundle args = new Bundle();
            args.putString("command", up.command);
            args.putInt("uid", up.uid);
            args.putInt("desiredUid", up.desiredUid);
            l.setArguments(args);
        }
        l.setListContentId(getId());
        setContent(l, up == null, up == null ? getString(R.string.logs) : up.getName());
    }

    protected LogFragment createLogFragment() {
        return new LogFragment();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        MenuInflater mi = new MenuInflater(getActivity());
        mi.inflate(R.menu.main, menu);
        MenuItem log = menu.findItem(R.id.logs);
        log.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                showAllLogs();
                return true;
            }
        });
    }

}
