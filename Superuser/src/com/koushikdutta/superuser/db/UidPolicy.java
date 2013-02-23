package com.koushikdutta.superuser.db;

import java.util.Date;

import com.koushikdutta.superuser.R;

public class UidPolicy extends UidCommand {
    public static final String ALLOW = "allow";
    public static final String DENY = "deny";
    public static final String INTERACTIVE = "interactive";

    public String policy;
    public int until;
    public Date getUntilDate() {
        return new Date((long)until * 1000);
    }

    public int getPolicyResource() {
        if (ALLOW.equals(policy))
            return R.string.allow;
        else if (INTERACTIVE.equals(policy))
            return R.string.interactive;
        return R.string.deny;
    }
    
    public int last;
    public Date getLastDate() {
        return new Date((long)last * 1000);
    }
}
