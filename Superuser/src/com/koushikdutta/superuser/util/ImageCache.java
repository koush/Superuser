package com.koushikdutta.superuser.util;

import android.graphics.drawable.Drawable;

public final class ImageCache extends SoftReferenceHashTable<String, Drawable> {
    private static ImageCache mInstance = new ImageCache();
    
    public static ImageCache getInstance() {
        return mInstance;
    }
    
    private ImageCache() {
    }
}
