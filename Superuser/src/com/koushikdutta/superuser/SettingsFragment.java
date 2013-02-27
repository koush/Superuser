package com.koushikdutta.superuser;

import com.koushikdutta.widgets.SupportFragment;

public class SettingsFragment extends SupportFragment<SettingsFragmentInternal> {

    @Override
    public SettingsFragmentInternal createFragmentInterface() {
        return new SettingsFragmentInternal(this);
    }

}
