package com.koushikdutta.superuser.db;

import com.koushikdutta.superuser.R;

public class UidPolicy extends UidCommand {
    public static final String ALLOW = "allow";
    public static final String DENY = "deny";
    public static final String INTERACTIVE = "interactive";

    public String policy;
    public int until;
    
    public int getPolicyResource() {
        if (ALLOW.equals(policy))
            return R.string.allow;
        else if (INTERACTIVE.equals(policy))
            return R.string.interactive;
        return R.string.deny;
    }
}
