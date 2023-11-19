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
