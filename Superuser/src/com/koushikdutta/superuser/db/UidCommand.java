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
        return String.valueOf(uid);
    }
}
