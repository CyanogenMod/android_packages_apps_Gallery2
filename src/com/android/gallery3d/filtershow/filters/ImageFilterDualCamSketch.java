/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.cache.BitmapCache;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.pipeline.FilterEnvironment;
import com.android.gallery3d.filtershow.tools.DualCameraNativeEngine;

public class ImageFilterDualCamSketch extends ImageFilter {
    private static final String TAG = ImageFilterDualCamSketch.class.getSimpleName();
    private static Toast sSegmentToast;

    private FilterDualCamSketchRepresentation mParameters;
    private Paint mPaint = new Paint();
    private Bitmap mSketchBm = null;
    private int mSketchResId = 0;
    private Resources mResources = null;

    public FilterRepresentation getDefaultRepresentation() {
        return null;
    }

    public void useRepresentation(FilterRepresentation representation) {
        FilterDualCamSketchRepresentation parameters = (FilterDualCamSketchRepresentation) representation;
        mParameters = parameters;
    }

    public FilterDualCamSketchRepresentation getParameters() {
        return mParameters;
    }

    public void setResources(Resources resources) {
        mResources = resources;
    }

    @Override
    public void freeResources() {
        if (mSketchBm != null)
            mSketchBm.recycle();

        mSketchBm = null;
        mSketchResId = 0;
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, int quality) {
        if (getParameters() == null) {
            return bitmap;
        }

        Point point = getParameters().getPoint();
        if(!point.equals(-1,-1)) {

            int sketchResId = getParameters().getSketchResId();
            if (sketchResId == 0) {
                return bitmap;
            }

            if (mSketchBm == null || mSketchResId != sketchResId) {
                loadSketchImage(sketchResId);
            }

            if (mSketchBm == null) {
                return bitmap;
            }

            boolean result = false;
            Bitmap filteredBitmap = null;

            Rect originalBounds = MasterImage.getImage().getOriginalBounds();
            int origW = originalBounds.width();
            int origH = originalBounds.height();

            filteredBitmap = MasterImage.getImage().getBitmapCache().getBitmap(origW, origH, BitmapCache.FILTERS);

            result = DualCameraNativeEngine.getInstance().applySketch(mSketchBm, point.x, point.y, filteredBitmap);

            if(result == false) {
                Log.e(TAG, "Imagelib API failed");
                sActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(sSegmentToast == null) {
                            sSegmentToast = Toast.makeText(sActivity, R.string.dualcam_no_segment_toast, Toast.LENGTH_SHORT);
                        }
                        sSegmentToast.show();
                    }
                });
                return bitmap;
            } else {

                if(quality == FilterEnvironment.QUALITY_FINAL) {
                    mPaint.reset();
                    mPaint.setAntiAlias(true);
                    mPaint.setFilterBitmap(true);
                    mPaint.setDither(true);
                }

                Canvas canvas = new Canvas(bitmap);
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();
                if(getEnvironment().getImagePreset().getDoApplyGeometry()) {
                    Matrix originalToScreen = getOriginalToScreenMatrix(w, h);
                    canvas.drawBitmap(filteredBitmap, originalToScreen, mPaint);
                } else {
                    canvas.drawBitmap(filteredBitmap, null, new Rect(0,0,w,h), mPaint);
                }

                MasterImage.getImage().getBitmapCache().cache(filteredBitmap);
            }
        }

        return bitmap;
    }

    private void loadSketchImage(int sketchResId) {
        if(mResources == null) {
            Log.w(TAG, "resources not set");
            return;
        }

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        mSketchResId = sketchResId;
        if (mSketchResId != 0) {
            mSketchBm = BitmapFactory.decodeResource(mResources, mSketchResId, o);
            if(mSketchBm == null) {
                Log.w(TAG, "could not decode sketch image");
            }
        } else {
            Log.w(TAG, "bad sketch resource for filter: " + mName);
        }
    }
}
