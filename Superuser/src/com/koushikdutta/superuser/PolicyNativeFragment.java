package com.koushikdutta.superuser;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.koushikdutta.widgets.NativeFragment;

public class PolicyNativeFragment extends NativeFragment<PolicyFragmentInternal> {
    ContextThemeWrapper mWrapper;
    public Context getContext(Context ctx) {
        if (mWrapper != null)
            return mWrapper;
        mWrapper = new ContextThemeWrapper(ctx, R.style.SuperuserDark);
        return mWrapper;
    }

    @SuppressLint("NewApi")
    @Override
    public void onDetach() {
        super.onDetach();
        System.out.println("ondetach");
        Fragment c = getFragmentManager().findFragmentByTag("content");
        if (c != null)
            getFragmentManager().beginTransaction().remove(c).commit();
        else
            System.out.println("nothing");
    }
    
    @Override
    public PolicyFragmentInternal createFragmentInterface() {
        return new PolicyFragmentInternal(this) {
            @Override
            public Context getContext() {
                return PolicyNativeFragment.this.getContext(super.getContext());
            }
            
            @Override
            protected int getListFragmentResource() {
                return R.layout.policy_list_content;
            }
            
            @Override
            protected void setPadding() {
                getListView().setPadding(0, 0, 0, 0);
                getContainer().setPadding(0, 0, 0, 0);
            }
        };
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return super.onCreateView((LayoutInflater)getContext(inflater.getContext()).getSystemService(Context.LAYOUT_INFLATER_SERVICE), container, savedInstanceState);
    }
}
