package com.koushikdutta.superuser;

import android.content.Context;
import android.view.ContextThemeWrapper;

import com.koushikdutta.widgets.NativeFragment;


public class SettingsNativeFragment extends NativeFragment<SettingsFragmentInternal> {
    @Override
    public SettingsFragmentInternal createFragmentInterface() {
        return new SettingsFragmentInternal(this) {
            ContextThemeWrapper mWrapper;
            @Override
            public Context getContext() {
                if (mWrapper != null)
                    return mWrapper;
                Context ctx = super.getContext();
                mWrapper = new ContextThemeWrapper(ctx, R.style.SuperuserDark);
                return mWrapper;
            }
        };
    }
}
