package com.koushikdutta.superuser;

import java.lang.reflect.Method;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.UserManager;

import com.koushikdutta.superuser.util.ImageCache;

public class Helper {
    public static Drawable loadPackageIcon(Context context, String pn) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = context.getPackageManager().getPackageInfo(pn, 0);
            Drawable ret = ImageCache.getInstance().get(pn);
            if (ret != null)
                return ret;
            ImageCache.getInstance().put(pn, ret = pi.applicationInfo.loadIcon(pm));
            return ret;
        }
        catch (Exception ex) {
        }
        return null;
    }

    @SuppressLint("NewApi")
    public static boolean supportsMultipleUsers(Context context) {
        final UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
        try {
            Method supportsMultipleUsers = UserManager.class.getMethod("supportsMultipleUsers");
            return (Boolean)supportsMultipleUsers.invoke(um);
        }
        catch (Exception ex) {
            return false;
        }
    }

    @SuppressLint("NewApi")
    public static boolean isAdminUser(Context context) {
        final UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
        try {
            Method getUserHandle = UserManager.class.getMethod("getUserHandle");
            int userHandle = (Integer)getUserHandle.invoke(um);
            return userHandle == 0;
        }
        catch (Exception ex) {
            return true;
        }
    }
}
