package com.koushikdutta.superuser;

import java.util.ArrayList;

import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.widget.ImageView;

import com.koushikdutta.superuser.db.SuDatabaseHelper;
import com.koushikdutta.superuser.db.UidPolicy;
import com.koushikdutta.widgets.BetterListActivity;
import com.koushikdutta.widgets.BetterListFragment;
import com.koushikdutta.widgets.ListContentFragment;
import com.koushikdutta.widgets.ListItem;

public class MainActivity extends BetterListActivity {
    public MainActivity() {
        super(ListContentFragment.class);
    }

    public ListContentFragment getFragment() {
        return (ListContentFragment)super.getFragment();
    }
    
    void showAllLogs() {
        setContent(null, null);
        getFragment().getListView().clearChoices();
    }
    
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        MenuInflater mi = new MenuInflater(this);
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
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                getFragment().setContent(new SettingsFragment() {
                    @Override
                    public void onConfigurationChanged(Configuration newConfig) {
                        super.onConfigurationChanged(newConfig);
                        onMenuItemClick(item);
                    }
                }, true);
                return true;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }
    
    BetterListFragment mContent;
    @Override
    public void onCreate(Bundle savedInstanceState, View view) {
        super.onCreate(savedInstanceState, view);

        getFragment().setEmpty(R.string.no_apps);
        
        load();

        ImageView watermark = (ImageView)view.findViewById(R.id.watermark);
        if (watermark != null)
            watermark.setImageResource(R.drawable.clockwork512);
        if (!getFragment().isPaged())
            showAllLogs();
        
    }
    
    public void onBackPressed() {
        if (getFragment().onBackPressed())
            return;
        super.onBackPressed();
    };
    
    void setContent(final ListItem li, final UidPolicy up) {
        mContent = new LogFragment() {
            @Override
            void onDelete() {
                super.onDelete();
                getFragment().removeItem(li);
                showAllLogs();
            }

            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                super.onConfigurationChanged(newConfig);
                setContent(li, up);
            }
        }
        .setUidPolicy(up);
        getFragment().setContent(mContent, up == null);
    }

    void addPolicy(final UidPolicy up) {
        java.text.DateFormat df = DateFormat.getLongDateFormat(MainActivity.this);
        String date = df.format(up.getLastDate());
        if (up.last == 0)
            date = null;
        ListItem li = addItem(up.getPolicyResource(), new ListItem(getFragment(), up.name, date) {
            public void onClick(View view) {
                super.onClick(view);

                setContent(this, up);
            };
        });
        if (up.icon == null)
            li.setIcon(R.drawable.ic_launcher);
        else
            li.setDrawable(up.icon);
    }
    
    Handler mHandler = new Handler();
    void load() {
        final ArrayList<UidPolicy> policies = SuDatabaseHelper.getPolicies(MainActivity.this);
        
        for (UidPolicy up: policies) {
            addPolicy(up);
        }
    }
}
