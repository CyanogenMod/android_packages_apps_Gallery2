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
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.editors.TrueScannerEditor;
import com.android.gallery3d.filtershow.imageshow.ImageTrueScanner;
import com.android.gallery3d.filtershow.imageshow.MasterImage;

import static com.android.gallery3d.filtershow.imageshow.ImageTrueScanner.*;

public class TrueScannerActs extends SimpleImageFilter {
    public static final String SERIALIZATION_NAME = "TrueScannerActs";
    private static final int MIN_WIDTH = 512;
    private static final int MIN_HEIGHT = 512;
    private static final boolean DEBUG = false;
    private static boolean isTrueScannerEnabled = true;
    private static boolean isPointsAcquired;
    private final static int POINTS_LEN = 8;
    private static int[] mAcquiredPoints = new int[POINTS_LEN+2];
    protected boolean isWhiteBoard = false;
    private boolean mLocked = false;
    private Bitmap rectifiedImage = null;
    private Bitmap whiteboardImage = null;
    private int[] oldPts = new int[POINTS_LEN];

    private void printDebug(String str) {
        if(DEBUG)
            android.util.Log.d("TrueScanner", str);
    }

    public static boolean isTrueScannerEnabled() {
        return isTrueScannerEnabled;
    }

    public TrueScannerActs() {
        mName = "TrueScannerActs";
        isPointsAcquired = false;
    }

    public FilterRepresentation getDefaultRepresentation() {
        FilterBasicRepresentation representation = (FilterBasicRepresentation) super.getDefaultRepresentation();
        representation.setName("TrueScanner");
        representation.setSerializationName(SERIALIZATION_NAME);
        representation.setFilterClass(TrueScannerActs.class);
        representation.setTextId(R.string.truescanner_normal);
        representation.setMinimum(0);
        representation.setMaximum(10);
        representation.setValue(0);
        representation.setDefaultValue(0);
        representation.setSupportsPartialRendering(false);
        representation.setEditorId(TrueScannerEditor.ID);

        return representation;
    }

    private native int[] processImage(Bitmap orgBitmap, Bitmap rectifiedBitmap, Bitmap whiteboardBitmap, int[] cornerPts);
    private native int[] getPoints(Bitmap orgBitmap);

    public static int[] getPoints() {
        if(!isPointsAcquired)
            return null;
        return mAcquiredPoints;
    }

    private synchronized boolean acquireLock(boolean isAcquiring) {
        if(mLocked != isAcquiring) {
            mLocked = isAcquiring;
            return true;
        }
        return false;
    }

    private boolean isReNewed(int pts[]) {
        if(rectifiedImage == null || whiteboardImage == null) {
            return true;
        } else {
            for(int i=0; i<oldPts.length;i++) {
                if(pts[i] != oldPts[i]) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float not_use, int quality) {
        if(bitmap == null)
            return null;

        if(bitmap.getWidth() <= MIN_WIDTH && bitmap.getHeight() <= MIN_HEIGHT)
            return bitmap;

        if(!acquireLock(true)) {
            sActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(sActivity, "Still processing the previous request... ", Toast.LENGTH_LONG).show();
                }
            });

            return bitmap;
        }

        if(!isPointsAcquired) {
            int[] recPoints = getPoints(bitmap);
            for(int i=0; i < POINTS_LEN; i++) {
                mAcquiredPoints[i] = recPoints[i];
            }
            mAcquiredPoints[POINTS_LEN] = bitmap.getWidth();
            mAcquiredPoints[POINTS_LEN+1] = bitmap.getHeight();
            isPointsAcquired = true;
        }

        int[] pts = ImageTrueScanner.getDeterminedPoints();
        int[] resultPts = new int[POINTS_LEN];
        float xScale = ((float)bitmap.getWidth())/pts[POINTS_LEN];
        float yScale = ((float)bitmap.getHeight())/pts[POINTS_LEN+1];
        for(int i=0; i < POINTS_LEN; i++) {
            if(i%2 == 0)
                resultPts[i] = (int)((pts[i] - pts[POINTS_LEN+2])*xScale);
            else
                resultPts[i] = (int)((pts[i] - pts[POINTS_LEN+3])*yScale);
        }

        if(isReNewed(pts)) {
            rectifiedImage = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            whiteboardImage = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            int[] outputSize = processImage(bitmap, rectifiedImage, whiteboardImage, resultPts);
            rectifiedImage = Bitmap.createBitmap(rectifiedImage, 0, 0, outputSize[0], outputSize[1]);
            whiteboardImage = Bitmap.createBitmap(whiteboardImage, 0, 0, outputSize[2], outputSize[3]);
        }

        acquireLock(false);
        if(isWhiteBoard)
            return whiteboardImage;

        return rectifiedImage;
    }

    static {
        try {
            System.loadLibrary("jni_truescanner");
            isTrueScannerEnabled = true;
        } catch(UnsatisfiedLinkError e) {
            isTrueScannerEnabled = false;
        }
    }
}
