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
import java.util.Date;

import android.content.Context;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.widget.ImageView;

import com.koushikdutta.superuser.db.LogEntry;
import com.koushikdutta.superuser.db.SuDatabaseHelper;
import com.koushikdutta.superuser.db.SuperuserDatabaseHelper;
import com.koushikdutta.superuser.db.UidPolicy;
import com.koushikdutta.widgets.FragmentInterfaceWrapper;
import com.koushikdutta.widgets.ListContentFragmentInternal;
import com.koushikdutta.widgets.ListItem;

public class PolicyFragmentInternal extends ListContentFragmentInternal {
    public PolicyFragmentInternal(FragmentInterfaceWrapper fragment) {
        super(fragment);
    }
    
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
            for (UidPolicy up: policies) {
                int last = 0;
                ArrayList<LogEntry> logs = SuperuserDatabaseHelper.getLogs(db, up, 1);
                if (logs.size() > 0)
                    last = logs.get(0).date;
                addPolicy(up, last);
            }
        }
        finally {
            db.close();
        }
    }
    
    public void onResume() {
        super.onResume();
        load();
    }

    FragmentInterfaceWrapper mContent;

    
    @Override
    public void onCreate(Bundle savedInstanceState, View view) {
        super.onCreate(savedInstanceState, view);

        getFragment().setHasOptionsMenu(true);
        
        setEmpty(R.string.no_apps);
        
        load();

        if ("com.koushikdutta.superuser".equals(getContext().getPackageName())) {
            ImageView watermark = (ImageView)view.findViewById(R.id.watermark);
            if (watermark != null)
                watermark.setImageResource(R.drawable.clockwork512);
        }
        if (!isPaged())
            showAllLogs();
    }
    
    public Date getLastDate(int last) {
        return new Date((long)last * 1000);
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
            };
        });
        
        Drawable icon = Helper.loadPackageIcon(getActivity(), up.packageName);
        if (icon == null)
            li.setIcon(R.drawable.ic_launcher);
        else
            li.setDrawable(icon);
    }
    
    public void onConfigurationChanged(Configuration newConfig) {
    };

    protected LogNativeFragment createLogNativeFragment() {
        return new LogNativeFragment();
    }
    
    protected SettingsNativeFragment createSettingsNativeFragment() {
        return new SettingsNativeFragment();
    }

    FragmentInterfaceWrapper setContentNative(final ListItem li, final UidPolicy up) {
        LogNativeFragment l = createLogNativeFragment();
        l.getInternal().setUidPolicy(up);
        if (up != null) {
            Bundle args = new Bundle();
            args.putString("command", up.command);
            args.putInt("uid", up.uid);
            args.putInt("desiredUid", up.desiredUid);
            l.setArguments(args);
        }
        l.getInternal().setListContentId(getFragment().getId());
        return l;
    }
    
    void setContent(final ListItem li, final UidPolicy up) {
        if (getActivity() instanceof FragmentActivity) {
            LogFragment l = new LogFragment();
            l.getInternal().setUidPolicy(up);
            l.getInternal().setListContentId(getFragment().getId());
            mContent = l;
        }
        else {
            mContent = setContentNative(li, up);
        }
        
        setContent(mContent, up == null, up == null ? getString(R.string.logs) : up.getName());
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
        
        MenuItem settings = menu.findItem(R.id.settings);
        settings.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            void openSettingsNative(final MenuItem item) {
                setContent(createSettingsNativeFragment(), true, getString(R.string.settings));
            }
            
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                if (getActivity() instanceof FragmentActivity) {
                    setContent(new SettingsFragment(), true, getString(R.string.settings));
                }
                else {
                    openSettingsNative(item);
                }
                return true;
            }
        });
    }

}
