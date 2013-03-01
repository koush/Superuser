/*
 * Copyright (C) 2013 Koushik Dutta (@koush)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
