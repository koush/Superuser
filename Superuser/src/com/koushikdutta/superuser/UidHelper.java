package com.koushikdutta.superuser;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import com.koushikdutta.superuser.db.UidCommand;
import com.koushikdutta.superuser.util.ImageCache;

public class UidHelper {
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
    
    public static void getPackageInfoForUid(Context context, UidCommand cpi, boolean loadIcon) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = context.getPackageManager().getPackageInfo(pm.getPackagesForUid(cpi.uid)[0], 0);
            cpi.name = pi.applicationInfo.loadLabel(pm).toString();
            cpi.packageName = pi.packageName;
            if (loadIcon) {
                cpi.icon = loadPackageIcon(context, cpi.packageName);
            }
        }
        catch (Exception ex) {
        }
    }
}
