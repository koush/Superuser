package com.koushikdutta.superuser.db;


public class UidCommand {
    public String username;
    public String name;
    public String packageName;
    public int uid;
    public String command;
    public int desiredUid;
    public String desiredName;
    
    public String getName() {
        if (name != null)
            return name;
        if (packageName != null)
            return packageName;
        if (username != null && username.length() > 0)
            return username;
        return String.valueOf(uid);
    }
}
