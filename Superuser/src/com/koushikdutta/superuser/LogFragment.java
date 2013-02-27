package com.koushikdutta.superuser;

import com.koushikdutta.widgets.SupportFragment;

public class LogFragment extends SupportFragment<LogFragmentInternal> {

    @Override
    public LogFragmentInternal createFragmentInterface() {
        return new LogFragmentInternal(this) {
            @Override
            void onDelete() {
                super.onDelete();
                LogFragment.this.onDelete();
            }
        };
    }

    void onDelete() {
        
    }
}
