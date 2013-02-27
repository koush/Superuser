package com.koushikdutta.superuser;

import android.content.Context;
import android.view.ContextThemeWrapper;

import com.koushikdutta.widgets.NativeFragment;

public class PolicyNativeFragment extends NativeFragment<PolicyFragmentInternal> {

    @Override
    public PolicyFragmentInternal createFragmentInterface() {
        return new PolicyFragmentInternal(this) {
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
