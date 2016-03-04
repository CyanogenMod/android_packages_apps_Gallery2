/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.HazeBusterEditor;

import static com.android.gallery3d.filtershow.imageshow.ImageTrueScanner.*;

public class HazeBusterActs extends SimpleImageFilter {
    public static final String SERIALIZATION_NAME = "HazeBusterActs";
    private static final boolean DEBUG = true;
    private static final int MIN_WIDTH = 512;
    private static final int MIN_HEIGHT = 512;
    private static final String TAG = "HazeBuster";
    private static boolean isHazeBusterEnabled = true;


    private void printDebug(String str) {
        if(DEBUG)
            android.util.Log.d(TAG, str);
    }

    public static boolean isHazeBusterEnabled() {
        return isHazeBusterEnabled;
    }

    public HazeBusterActs() {
        mName = "HazeBusterActs";
    }

    public FilterRepresentation getDefaultRepresentation() {
        FilterBasicRepresentation representation = (FilterBasicRepresentation) super.getDefaultRepresentation();
        representation.setName("HazeBuster");
        representation.setSerializationName(SERIALIZATION_NAME);
        representation.setFilterClass(HazeBusterActs.class);
        representation.setTextId(R.string.hazebuster_acts);
        representation.setSupportsPartialRendering(false);
        representation.setIsBooleanFilter(true);
        representation.setShowParameterValue(false);
        representation.setValue(0);
        representation.setDefaultValue(0);
        representation.setMinimum(0);
        representation.setMaximum(0);
        representation.setEditorId(HazeBusterEditor.ID);

        return representation;
    }

    private native boolean processImage(Bitmap bitmap, int width, int height, Bitmap dstBitmap);

    @Override
    public Bitmap apply(Bitmap bitmap, float not_use, int quality) {
        if(bitmap == null)
            return null;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if(width <= MIN_WIDTH && height <= MIN_HEIGHT)
            return bitmap;

        Bitmap outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        if(!processImage(bitmap, width, height, outBitmap)) {
            Log.e(TAG, "Image error on processing");
            return bitmap;
        }

        return outBitmap;
    }

    static {
        try {
            System.loadLibrary("jni_hazebuster");
            isHazeBusterEnabled = true;
        } catch(UnsatisfiedLinkError e) {
            isHazeBusterEnabled = false;
        }
    }
}
