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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.pipeline.FilterEnvironment;
import com.android.gallery3d.filtershow.tools.DualCameraNativeEngine;

public class ImageFilterDualCamera extends ImageFilter {
    private static final String TAG = ImageFilterDualCamera.class.getSimpleName();

    private FilterDualCamBasicRepresentation mParameters;
    private Paint mPaint = new Paint();
    private Bitmap mFilteredBitmap = null;
    private Point mPoint = null;
    private int mIntensity = -1;

    public FilterRepresentation getDefaultRepresentation() {
        return null;
    }

    public void useRepresentation(FilterRepresentation representation) {
        FilterDualCamBasicRepresentation parameters = (FilterDualCamBasicRepresentation) representation;
        mParameters = parameters;
    }

    public FilterDualCamBasicRepresentation getParameters() {
        return mParameters;
    }

    @Override
    public void freeResources() {
        if(mFilteredBitmap != null)
            mFilteredBitmap.recycle();

        mFilteredBitmap = null;
        mPoint = null;
        mIntensity = -1;
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, int quality) {
        if (getParameters() == null) {
            return bitmap;
        }

        Point point = getParameters().getPoint();
        int intensity = getParameters().getValue();

        if(!point.equals(-1,-1)) {

            // if parameters change, generate new filtered bitmap
            if(!point.equals(mPoint) || mIntensity != intensity) {
                mPoint = point;
                mIntensity = intensity;

                if(mFilteredBitmap == null) {
                    Rect originalBounds = MasterImage.getImage().getOriginalBounds();
                    int origW = originalBounds.width();
                    int origH = originalBounds.height();

                    mFilteredBitmap = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888);
                }

                boolean result = false;

                switch(mParameters.getTextId()) {
                case R.string.focus:
                    result = DualCameraNativeEngine.getInstance().applyFocus(mPoint.x, mPoint.y, mIntensity,
                            mFilteredBitmap);
                    break;
                case R.string.halo:
                    result = DualCameraNativeEngine.getInstance().applyHalo(mPoint.x, mPoint.y, mIntensity,
                            mFilteredBitmap);
                    break;
                case R.string.blur:
                    result = DualCameraNativeEngine.getInstance().applyBokeh(mPoint.x, mPoint.y, mIntensity,
                            mFilteredBitmap);
                    break;
                }

                if(result == false) {
                    Log.e(TAG, "Imagelib API failed");
                    return bitmap;
                }
            }

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
                canvas.drawBitmap(mFilteredBitmap, originalToScreen, mPaint);
            } else {
                canvas.drawBitmap(mFilteredBitmap, null, new Rect(0,0,w,h), mPaint);
            }
        }

        return bitmap;
    }
}
