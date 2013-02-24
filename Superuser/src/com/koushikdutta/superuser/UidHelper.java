package com.koushikdutta.superuser;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

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
}
