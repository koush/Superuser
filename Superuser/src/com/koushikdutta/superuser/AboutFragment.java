package com.koushikdutta.superuser;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.View;

import com.koushikdutta.widgets.BetterListFragment;
import com.koushikdutta.widgets.ListItem;

public class AboutFragment extends BetterListFragment {
    ContextThemeWrapper mWrapper;
    @Override
    public Context getContext() {
        if (mWrapper != null)
            return mWrapper;
        mWrapper = new ContextThemeWrapper(super.getContext(), R.style.AboutTheme);
        return mWrapper;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState, View view) {
        super.onCreate(savedInstanceState, view);
        
        addItem(R.string.about, new ListItem(getInternal(), "Koushik Dutta", "@koush", R.drawable.koush) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                Intent i = new Intent();
                i.setClassName("com.twitter.android", "com.twitter.android.ProfileActivity");
                i.putExtra("screen_name", "koush");
                try {
                    startActivity(i);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }

            }
        });
        
        final String uri = "http://github.com/koush/Superuser";
        addItem(R.string.about, new ListItem(getInternal(), "Github", uri, R.drawable.github) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                Intent i = new Intent();
                i.setData(Uri.parse(uri));
                startActivity(i);
            }
        });

        addItem(R.string.apps, new ListItem(getInternal(), "ROM Manager", "The ultimate backup, restore, and ROM installation tool", R.drawable.clockwork512) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                super.onClick(view);
                Intent i = new Intent();
                i.setData(Uri.parse("market://details?id=com.koushikdutta.rommanager"));
                startActivity(i);
            }
        });        
        addItem(R.string.apps, new ListItem(getInternal(), "Carbon", "Android's missing backup solution", R.drawable.carbon512) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                super.onClick(view);
                Intent i = new Intent();
                i.setData(Uri.parse("market://details?id=com.koushikdutta.backup"));
                startActivity(i);
            }
        });        
        addItem(R.string.apps, new ListItem(getInternal(), "DeskSMS", "Seamlessly text message from your email, browser, or instant messenger", R.drawable.desksms) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                super.onClick(view);
                Intent i = new Intent();
                i.setData(Uri.parse("market://details?id=com.koushikdutta.desktopsms"));
                startActivity(i);
            }
        });
        addItem(R.string.apps, new ListItem(getInternal(), "Tether", "Use your phone's web connection on a laptop or PC", R.drawable.tether_icon_usb_512) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                super.onClick(view);
                Intent i = new Intent();
                i.setData(Uri.parse("market://details?id=com.koushikdutta.tether"));
                startActivity(i);
            }
        });
    }
}
