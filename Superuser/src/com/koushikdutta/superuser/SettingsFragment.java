package com.koushikdutta.superuser;

import android.os.Bundle;
import android.view.View;

import com.koushikdutta.superuser.util.Settings;
import com.koushikdutta.widgets.BetterListFragment;
import com.koushikdutta.widgets.ListItem;

public class SettingsFragment extends BetterListFragment {
    @Override
    protected int getListFragmentResource() {
        return R.layout.settings;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState, View view) {
        super.onCreate(savedInstanceState, view);

        addItem(R.string.settings, new ListItem(this, R.string.logging, R.string.logging_summary, R.drawable.ic_logging) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                Settings.setLogging(getActivity(), getChecked());
            }
        })
        .setCheckboxVisible(true)
        .setChecked(Settings.getLogging(getActivity()));
        
        addItem(R.string.settings, new ListItem(this, R.string.pin_and_password, R.string.pin_and_password_summary, R.drawable.ic_protected) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
            }
        });
        
        addItem(R.string.settings, new ListItem(this, R.string.request_timeout, R.string.request_timeout_summary, R.drawable.ic_timeout) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
            }
        });

        
        addItem(R.string.settings, new ListItem(this, R.string.notifications, R.string.notifications_summary, R.drawable.ic_notifications) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
            }
        });
    }
}
