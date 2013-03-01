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

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.superuser.db.LogEntry;
import com.koushikdutta.superuser.db.SuDatabaseHelper;
import com.koushikdutta.superuser.db.UidPolicy;
import com.koushikdutta.superuser.util.Settings;
import com.koushikdutta.widgets.BetterListFragmentInternal;
import com.koushikdutta.widgets.FragmentInterfaceWrapper;
import com.koushikdutta.widgets.ListItem;

public class LogFragmentInternal extends BetterListFragmentInternal {
    public LogFragmentInternal(FragmentInterfaceWrapper fragment) {
        super(fragment);
    }

    UidPolicy up;
    public LogFragmentInternal setUidPolicy(UidPolicy up) {
        this.up = up;
        return this;
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
                    SuDatabaseHelper.deleteLogs(getActivity());
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
    protected int getListFragmentResource() {
        return R.layout.policy_fragment;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState, View view) {
        super.onCreate(savedInstanceState, view);
        
        getFragment().setHasOptionsMenu(true);
        
        if (up == null) {
            Bundle bundle = getFragment().getArguments();
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
            ((TextView)view.findViewById(R.id.command_header)).setText(up.command == null ? getString(R.string.all_commands) : up.command);
            String app = up.username;
            if (app == null || app.length() == 0)
                app = String.valueOf(up.uid);
            ((TextView)view.findViewById(R.id.app_header)).setText(app);
            ((TextView)view.findViewById(R.id.package_header)).setText(up.packageName);

            getListView().setSelector(android.R.color.transparent);

            logs = SuDatabaseHelper.getLogs(getActivity(), up, -1);
        }
        else {
            view.findViewById(R.id.title_container).setVisibility(View.GONE);
            logs = SuDatabaseHelper.getLogs(getActivity());
        }
        
        setEmpty(R.string.no_logs);
        
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

        final CompoundButton cb = (CompoundButton)view.findViewById(R.id.logging);
        cb.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (up == null) {
                    Settings.setLogging(getActivity(), cb.isChecked());
                }
                else {
                    up.logging = cb.isChecked();
                    SuDatabaseHelper.setPolicy(getActivity(), up);
                }
            }
        });
        if (up == null) {
            cb.setChecked(Settings.getLogging(getActivity()));
        }
        else {
            cb.setChecked(up.logging);
        }
    }
}
