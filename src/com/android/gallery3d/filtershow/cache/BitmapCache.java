/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.gallery3d.filtershow.cache;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;
import com.android.gallery3d.filtershow.pipeline.Buffer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

public class BitmapCache {
    private static final String LOGTAG = "BitmapCache";
    private HashMap<Long, ArrayList<WeakReference<Bitmap>>>
            mBitmapCache = new HashMap<Long, ArrayList<WeakReference<Bitmap>>>();
    private final int mMaxItemsPerKey = 4;

    public void cache(Buffer buffer) {
        if (buffer == null) {
            return;
        }
        Bitmap bitmap = buffer.getBitmap();
        cache(bitmap);
    }

    public synchronized void cache(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        Long key = calcKey(bitmap.getWidth(), bitmap.getHeight());
        ArrayList<WeakReference<Bitmap>> list = mBitmapCache.get(key);
        if (list == null) {
            list = new ArrayList<WeakReference<Bitmap>>();
            mBitmapCache.put(key, list);
        }
        if (list.size() < mMaxItemsPerKey) {
            for (int i = 0; i < list.size(); i++) {
                WeakReference<Bitmap> ref = list.get(i);
                if (ref.get() == bitmap) {
                    return; // bitmap already in the cache
                }
            }
            list.add(new WeakReference<Bitmap>(bitmap));
        }
    }

    public synchronized Bitmap getBitmap(int w, int h) {
        Long key = calcKey(w, h);
        WeakReference<Bitmap> ref = null; //mBitmapCache.remove(key);
        ArrayList<WeakReference<Bitmap>> list = mBitmapCache.get(key);
        if (list != null && list.size() > 0) {
            ref = list.remove(0);
            if (list.size() == 0) {
                mBitmapCache.remove(key);
            }
        }
        Bitmap bitmap = null;
        if (ref != null) {
            bitmap = ref.get();
        }
        if (bitmap == null
                || bitmap.getWidth() != w
                || bitmap.getHeight() != h) {
            bitmap = Bitmap.createBitmap(
                    w, h, Bitmap.Config.ARGB_8888);
        }
        return bitmap;
    }

    public Bitmap getBitmapCopy(Bitmap source) {
        Bitmap bitmap = getBitmap(source.getWidth(), source.getHeight());
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(source, 0, 0, null);
        return bitmap;
    }

    private Long calcKey(long w, long h) {
        return (w << 32) | h;
    }
}
