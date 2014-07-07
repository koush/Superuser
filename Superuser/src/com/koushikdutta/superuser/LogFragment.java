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

import java.util.ArrayList;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.superuser.db.LogEntry;
import com.koushikdutta.superuser.db.SuDatabaseHelper;
import com.koushikdutta.superuser.db.SuperuserDatabaseHelper;
import com.koushikdutta.superuser.db.UidPolicy;
import com.koushikdutta.superuser.util.Settings;
import com.koushikdutta.widgets.BetterListFragment;
import com.koushikdutta.widgets.ListItem;

public class LogFragment extends BetterListFragment {
    UidPolicy up;
    public LogFragment setUidPolicy(UidPolicy up) {
        this.up = up;
        return this;
    }

    int mListContentId;
    public void setListContentId(int id) {
        mListContentId = id;
    }

    public int getListContentId() {
        return mListContentId;
    }

    void onDelete() {
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

        inflater.inflate(R.menu.policy, menu);
        MenuItem delete = menu.findItem(R.id.delete);
        delete.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (up != null)
                    SuDatabaseHelper.delete(getActivity(), up);
                else
                    SuperuserDatabaseHelper.deleteLogs(getActivity());
                onDelete();
                return true;
            }
        });
    }

    @Override
    protected int getListItemResource() {
        return R.layout.log_item;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState, View view) {
        super.onCreate(savedInstanceState, view);

        LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        getListView().addHeaderView(inflater.inflate(R.layout.policy_header, null));

        setHasOptionsMenu(true);

        if (up == null) {
            Bundle bundle = getArguments();
            if (bundle != null) {
                String command = bundle.getString("command");
                int uid = bundle.getInt("uid", -1);
                int desiredUid = bundle.getInt("desiredUid", -1);
                if (uid != -1 && desiredUid != -1) {
                    up = SuDatabaseHelper.get(getContext(), uid, desiredUid, command);
                }
            }
        }

        ArrayList<LogEntry> logs;
        java.text.DateFormat day = DateFormat.getDateFormat(getActivity());
        java.text.DateFormat time = DateFormat.getTimeFormat(getActivity());
        if (up != null) {
            ImageView icon = (ImageView)view.findViewById(R.id.image);
            icon.setImageDrawable(Helper.loadPackageIcon(getActivity(), up.packageName));
            TextView name = (TextView)view.findViewById(R.id.name);
            name.setText(up.name);

            ((TextView)view.findViewById(R.id.uid_header)).setText(Integer.toString(up.desiredUid));
            ((TextView)view.findViewById(R.id.command_header)).setText(TextUtils.isEmpty(up.command) ? getString(R.string.all_commands) : up.command);
            String app = up.username;
            if (app == null || app.length() == 0)
                app = String.valueOf(up.uid);
            ((TextView)view.findViewById(R.id.app_header)).setText(app);
            ((TextView)view.findViewById(R.id.package_header)).setText(up.packageName);

            getListView().setSelector(android.R.color.transparent);

            logs = SuperuserDatabaseHelper.getLogs(getActivity(), up, -1);
        }
        else {
            TextView empty = (TextView)getLayoutInflater(savedInstanceState).inflate(R.layout.empty, null);
            ((ViewGroup)view.findViewById(R.id.empty)).addView(empty);
            empty.setText(R.string.no_logs);
            view.findViewById(R.id.policy_header).setVisibility(View.GONE);
            logs = SuperuserDatabaseHelper.getLogs(getActivity());
        }

        for (LogEntry log: logs) {
            final String date = time.format(log.getDate());
            String title = date;
            String summary = getString(log.getActionResource());
            if (up == null) {
                title = log.getName();
            }
            addItem(day.format(log.getDate()), new ListItem(this, title, summary, null) {
                @Override
                public View getView(android.content.Context context, View convertView) {
                    View ret = super.getView(context, convertView);
                    if (up == null) {
                        ((TextView)ret.findViewById(R.id.extra)).setText(date);
                    }
                    return ret;
                }
            });
        }

        final CompoundButton logging = (CompoundButton)view.findViewById(R.id.logging);
        if (up == null) {
            logging.setChecked(Settings.getLogging(getActivity()));
        }
        else {
            logging.setChecked(up.logging);
        }
        logging.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                if (up == null) {
                    Settings.setLogging(getActivity(), isChecked);
                }
                else {
                    up.logging = isChecked;
                    SuDatabaseHelper.setPolicy(getActivity(), up);
                }
            }
        });

        final CompoundButton notification = (CompoundButton)view.findViewById(R.id.notification);
        if (up == null) {
            view.findViewById(R.id.notification_container).setVisibility(View.GONE);
        }
        else {
            notification.setChecked(up.notification);
        }
        notification.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                if (up == null) {
                }
                else {
                    up.notification = isChecked;
                    SuDatabaseHelper.setPolicy(getActivity(), up);
                }
            }
        });
    }
}
