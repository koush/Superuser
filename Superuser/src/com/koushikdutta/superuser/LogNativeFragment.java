package com.koushikdutta.superuser;

import android.content.Context;
import android.view.ContextThemeWrapper;

import com.koushikdutta.widgets.NativeFragment;

public class LogNativeFragment extends NativeFragment<LogFragmentInternal> {

    @Override
    public LogFragmentInternal createFragmentInterface() {
        return new LogFragmentInternal(this) {
            @Override
            void onDelete() {
                super.onDelete();
                LogNativeFragment.this.onDelete();
            }
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

    void onDelete() {
        
    }
}
