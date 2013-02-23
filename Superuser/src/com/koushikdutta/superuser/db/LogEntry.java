package com.koushikdutta.superuser.db;

import java.util.Date;

import com.koushikdutta.superuser.R;

public class LogEntry extends UidCommand {
    public long id;
    public String action;
    public int date;
    
    public Date getDate() {
        return new Date((long)date * 1000);
    }

    public int getActionResource() {
        if (UidPolicy.ALLOW.equals(action))
            return R.string.allow;
        else if (UidPolicy.INTERACTIVE.equals(action))
            return R.string.interactive;
        return R.string.deny;
    }
}
