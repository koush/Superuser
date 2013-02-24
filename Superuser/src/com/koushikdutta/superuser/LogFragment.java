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
import com.koushikdutta.widgets.BetterListFragment;
import com.koushikdutta.widgets.ListItem;

public class LogFragment extends BetterListFragment {
    UidPolicy up;
    public LogFragment setUidPolicy(UidPolicy up) {
        this.up = up;
        return this;
    }
    
    void onDelete() {
        
    }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        
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
        
        setHasOptionsMenu(true);
        
        ArrayList<LogEntry> logs;
        java.text.DateFormat day = DateFormat.getDateFormat(getActivity());
        java.text.DateFormat time = DateFormat.getTimeFormat(getActivity());
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
                title = log.name;
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
