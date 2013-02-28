package com.koushikdutta.superuser;

import com.koushikdutta.widgets.BetterListActivity;

public class MainActivity extends BetterListActivity {
    public MainActivity() {
        super(PolicyFragment.class);
    }

    public PolicyFragmentInternal getFragment() {
        return (PolicyFragmentInternal)super.getFragment();
    }
}
