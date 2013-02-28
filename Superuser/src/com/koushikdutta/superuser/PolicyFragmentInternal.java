package com.koushikdutta.superuser;

import java.util.ArrayList;

import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.widget.ImageView;

import com.koushikdutta.superuser.db.SuDatabaseHelper;
import com.koushikdutta.superuser.db.UidPolicy;
import com.koushikdutta.widgets.FragmentInterfaceWrapper;
import com.koushikdutta.widgets.ListContentFragmentInternal;
import com.koushikdutta.widgets.ListItem;

public class PolicyFragmentInternal extends ListContentFragmentInternal {
    public PolicyFragmentInternal(FragmentInterfaceWrapper fragment) {
        super(fragment);
    }
    
    void showAllLogs() {
        setContent(null, null);
        getListView().clearChoices();
    }
    
    void load() {
        final ArrayList<UidPolicy> policies = SuDatabaseHelper.getPolicies(getActivity());
        
        for (UidPolicy up: policies) {
            addPolicy(up);
        }
    }

    FragmentInterfaceWrapper mContent;

    
    @Override
    public void onCreate(Bundle savedInstanceState, View view) {
        super.onCreate(savedInstanceState, view);

        getFragment().setHasOptionsMenu(true);
        
        setEmpty(R.string.no_apps);
        
        load();

        ImageView watermark = (ImageView)view.findViewById(R.id.watermark);
        if (watermark != null)
            watermark.setImageResource(R.drawable.clockwork512);
        if (!isPaged())
            showAllLogs();
    }
    

    void addPolicy(final UidPolicy up) {
        java.text.DateFormat df = DateFormat.getLongDateFormat(getActivity());
        String date = df.format(up.getLastDate());
        if (up.last == 0)
            date = null;
        ListItem li = addItem(up.getPolicyResource(), new ListItem(this, up.name, date) {
            public void onClick(View view) {
                super.onClick(view);

                setContent(this, up);
            };
        })
        .setTheme(R.style.Superuser_PolicyIcon);
        Drawable icon = Helper.loadPackageIcon(getActivity(), up.packageName);
        if (icon == null)
            li.setIcon(R.drawable.ic_launcher);
        else
            li.setDrawable(icon);
    }
    
    public void onConfigurationChanged(Configuration newConfig) {
    };

    FragmentInterfaceWrapper setContentNative(final ListItem li, final UidPolicy up) {
        LogNativeFragment l = new LogNativeFragment();
//        {
//            @Override
//            void onDelete() {
//                super.onDelete();
//                removeItem(li);
//                showAllLogs();
//            }
//
//            @Override
//            public void onConfigurationChanged(Configuration newConfig) {
//                super.onConfigurationChanged(newConfig);
//                setContent(li, up);
//            }
//        };
        l.getInternal().setUidPolicy(up);
        if (up != null) {
            Bundle args = new Bundle();
            args.putString("command", up.command);
            args.putInt("uid", up.uid);
            args.putInt("desiredUid", up.desiredUid);
            l.setArguments(args);
        }
        return l;
    }
    
    void setContent(final ListItem li, final UidPolicy up) {
        if (getActivity() instanceof FragmentActivity) {
            LogFragment l = new LogFragment() {
                @Override
                void onDelete() {
                    super.onDelete();
                    removeItem(li);
                    showAllLogs();
                }

                @Override
                public void onConfigurationChanged(Configuration newConfig) {
                    super.onConfigurationChanged(newConfig);
                    setContent(li, up);
                }
            };
            l.getInternal().setUidPolicy(up);
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
                setContent(new SettingsNativeFragment() {
                    @Override
                    public void onConfigurationChanged(Configuration newConfig) {
                        super.onConfigurationChanged(newConfig);
                        onMenuItemClick(item);
                    }
                }, true, getString(R.string.settings));
            }
            
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                if (getActivity() instanceof FragmentActivity) {
                    setContent(new SettingsFragment() {
                        @Override
                        public void onConfigurationChanged(Configuration newConfig) {
                            super.onConfigurationChanged(newConfig);
                            onMenuItemClick(item);
                        }
                    }, true, getString(R.string.settings));
                }
                else {
                    openSettingsNative(item);
                }
                return true;
            }
        });
    }

}
