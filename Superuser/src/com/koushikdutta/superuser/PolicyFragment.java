package com.koushikdutta.superuser;

import com.koushikdutta.widgets.BetterListFragment;

public class PolicyFragment extends BetterListFragment {

    @Override
    public PolicyFragmentInternal createFragmentInterface() {
        return new PolicyFragmentInternal(this);
    }

}
