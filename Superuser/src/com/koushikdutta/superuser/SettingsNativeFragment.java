package com.koushikdutta.superuser;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.koushikdutta.widgets.NativeFragment;


public class SettingsNativeFragment extends NativeFragment<SettingsFragmentInternal> {
    ContextThemeWrapper mWrapper;
    public Context getContext(Context ctx) {
        if (mWrapper != null)
            return mWrapper;
        mWrapper = new ContextThemeWrapper(ctx, R.style.SuperuserDark);
        return mWrapper;
    }

    @Override
    public SettingsFragmentInternal createFragmentInterface() {
        return new SettingsFragmentInternal(this) {
            @Override
            public Context getContext() {
                return SettingsNativeFragment.this.getContext(super.getContext());
            }
        };
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return super.onCreateView((LayoutInflater)getContext(inflater.getContext()).getSystemService(Context.LAYOUT_INFLATER_SERVICE), container, savedInstanceState);
    }
}
