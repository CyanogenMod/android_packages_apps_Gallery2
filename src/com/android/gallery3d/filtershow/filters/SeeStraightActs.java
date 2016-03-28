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
import android.util.Log;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.SeeStraightEditor;

public class SeeStraightActs extends SimpleImageFilter {
    public static final String SERIALIZATION_NAME = "SeeStraightActs";
    private static final int MIN_WIDTH = 512;
    private static final int MIN_HEIGHT = 512;
    private static final int MIN_INPUT_REQUIREMENT = 640;
    private static final boolean DEBUG = true;
    private static boolean isSeeStraightEnabled = true;

    private void printDebug(String str) {
        if(DEBUG)
            android.util.Log.d("SeeStraight", str);
    }

    public static boolean isSeeStraightEnabled() {
        return isSeeStraightEnabled;
    }

    public SeeStraightActs() {
        mName = "SeeStraightActs";
    }

    public FilterRepresentation getDefaultRepresentation() {
        FilterBasicRepresentation representation = (FilterBasicRepresentation) super.getDefaultRepresentation();
        representation.setName("SeeStraight");
        representation.setSerializationName(SERIALIZATION_NAME);
        representation.setFilterClass(SeeStraightActs.class);
        representation.setTextId(R.string.seestraight_acts);
        representation.setMinimum(0);
        representation.setMaximum(10);
        representation.setValue(0);
        representation.setDefaultValue(0);
        representation.setSupportsPartialRendering(false);
        representation.setEditorId(SeeStraightEditor.ID);

        return representation;
    }

    private native int[] processImage(int width, int height, Bitmap srcBitmap, Bitmap dstBitmap);

    @Override
    public Bitmap apply(Bitmap bitmap, float not_use, int quality) {
        if(bitmap == null)
            return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if(width <= MIN_WIDTH && height <= MIN_HEIGHT)
            return bitmap;

        if(width <= MIN_INPUT_REQUIREMENT || height <= MIN_INPUT_REQUIREMENT) {
            sActivity.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(sActivity, sActivity.getResources().getString(R.string.seestraight_input_image_is_small), Toast.LENGTH_SHORT).show();
                }
            });
            return bitmap;
        }

        Bitmap dstBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] outputRoi = processImage(width, height, bitmap, dstBitmap);
        if(outputRoi == null) {
            sActivity.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(sActivity, sActivity.getResources().getString(R.string.seestraight_process_fail), Toast.LENGTH_SHORT).show();
                }
            });
            return bitmap;
        }
        dstBitmap = Bitmap.createBitmap(dstBitmap, outputRoi[0], outputRoi[1], outputRoi[2], outputRoi[3]);
        return dstBitmap;
    }

    static {
        try {
            System.loadLibrary("jni_seestraight");
            isSeeStraightEnabled = true;
        } catch(UnsatisfiedLinkError e) {
            isSeeStraightEnabled = false;
        }
    }
}
