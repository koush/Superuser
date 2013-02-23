package com.koushikdutta.superuser;

import java.util.ArrayList;

import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.superuser.db.LogEntry;
import com.koushikdutta.superuser.db.SuDatabaseHelper;
import com.koushikdutta.superuser.db.UidPolicy;
import com.koushikdutta.widgets.ActivityBase;
import com.koushikdutta.widgets.ActivityBaseFragment;
import com.koushikdutta.widgets.ListContentFragment;
import com.koushikdutta.widgets.ListItem;

public class MainActivity extends ActivityBase {
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
        return super.onCreateOptionsMenu(menu);
    }
    
    ActivityBaseFragment mContent;
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
        mContent = new ActivityBaseFragment() {
            @Override
            public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
                super.onCreateOptionsMenu(menu, inflater);
                
                inflater.inflate(R.menu.policy, menu);
                MenuItem delete = menu.findItem(R.id.delete);
                delete.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        getFragment().removeItem(li);
                        showAllLogs();
                        if (up != null)
                            SuDatabaseHelper.delete(MainActivity.this, up);
                        else
                            SuDatabaseHelper.deleteLogs(MainActivity.this);
                        return true;
                    }
                });
            }
            
            @Override
            protected int getListItemResource() {
                return R.layout.log_item;
            }
            
            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                super.onConfigurationChanged(newConfig);
                setContent(li, up);
            }
            
            @Override
            protected int getListFragmentResource() {
                return R.layout.policy_fragment;
            }
            
            @Override
            protected void onCreate(Bundle savedInstanceState, View view) {
                super.onCreate(savedInstanceState, view);
                
                setHasOptionsMenu(true);
                
                ArrayList<LogEntry> logs;
                java.text.DateFormat day = DateFormat.getDateFormat(MainActivity.this);
                java.text.DateFormat time = DateFormat.getTimeFormat(MainActivity.this);
                if (up != null) {
                    ImageView icon = (ImageView)view.findViewById(R.id.image);
                    icon.setImageDrawable(up.icon);
                    TextView name = (TextView)view.findViewById(R.id.name);
                    name.setText(up.name);
                    
                    ((TextView)view.findViewById(R.id.uid_header)).setText(Integer.toString(up.desiredUid));
                    ((TextView)view.findViewById(R.id.command_header)).setText(up.command);
                    String app = up.username;
                    if (app == null || app.length() == 0)
                        app = String.valueOf(up.uid);
                    ((TextView)view.findViewById(R.id.app_header)).setText(app);
                    ((TextView)view.findViewById(R.id.package_header)).setText(up.packageName);

                    
                    getListView().setSelector(android.R.color.transparent);

                    logs = SuDatabaseHelper.getLogs(MainActivity.this, up, -1);
                }
                else {
                    view.findViewById(R.id.title_container).setVisibility(View.GONE);
                    logs = SuDatabaseHelper.getLogs(MainActivity.this);
                }
                
                setEmpty(R.string.no_logs);
                
                for (LogEntry log: logs) {
                    final String date = time.format(log.getDate());
                    String title = date;
                    String summary = getString(log.getActionResource());
                    if (up == null) {
                        title = log.name;
                    }
                    mContent.addItem(day.format(log.getDate()), new ListItem(mContent, title, summary, null) {
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
            }
        };
        getFragment().setContent(mContent);
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
