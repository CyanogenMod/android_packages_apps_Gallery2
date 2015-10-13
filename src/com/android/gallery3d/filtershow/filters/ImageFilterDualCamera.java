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
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.cache.BitmapCache;
import com.android.gallery3d.filtershow.imageshow.GeometryMathUtils;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.imageshow.GeometryMathUtils.GeometryHolder;
import com.android.gallery3d.filtershow.pipeline.FilterEnvironment;
import com.android.gallery3d.filtershow.pipeline.ImagePreset;
import com.android.gallery3d.filtershow.tools.DualCameraNativeEngine;

public class ImageFilterDualCamera extends ImageFilter {
    private static final String TAG = ImageFilterDualCamera.class.getSimpleName();
    private static Toast sSegmentToast;

    private FilterDualCamBasicRepresentation mParameters;
    private Paint mPaint = new Paint();

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
    public Bitmap apply(Bitmap bitmap, float scaleFactor, int quality) {
        if (getParameters() == null) {
            return bitmap;
        }

        Point point = getParameters().getPoint();

        if(!point.equals(-1,-1)) {
            int value = getParameters().getValue();
            int maxValue = getParameters().getMaximum();
            float intensity = (float)value / (float)maxValue;
            Bitmap filteredBitmap = null;
            boolean result = false;
            int filteredW;
            int filteredH;

            if(quality == FilterEnvironment.QUALITY_FINAL) {
                Rect originalBounds = MasterImage.getImage().getOriginalBounds();
                filteredW = originalBounds.width();
                filteredH = originalBounds.height();
            } else {
                Bitmap originalBmp = MasterImage.getImage().getOriginalBitmapHighres();
                filteredW = originalBmp.getWidth();
                filteredH = originalBmp.getHeight();
            }

            filteredBitmap = MasterImage.getImage().getBitmapCache().getBitmap(filteredW, filteredH, BitmapCache.FILTERS);

            switch(mParameters.getTextId()) {
            case R.string.focus:
                result = DualCameraNativeEngine.getInstance().applyFocus(point.x, point.y, intensity,
                        quality != FilterEnvironment.QUALITY_FINAL, filteredBitmap);
                break;
            case R.string.halo:
                result = DualCameraNativeEngine.getInstance().applyHalo(point.x, point.y, intensity,
                        quality != FilterEnvironment.QUALITY_FINAL, filteredBitmap);
                break;
            }

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
                ImagePreset preset = getEnvironment().getImagePreset();
                int bmWidth = bitmap.getWidth();
                int bmHeight = bitmap.getHeight();

                if(preset.getDoApplyGeometry()) {
                    GeometryHolder holder = GeometryMathUtils.unpackGeometry(preset.getGeometryFilters());
                    GeometryMathUtils.drawTransformedCropped(holder, canvas, filteredBitmap, bmWidth, bmHeight);
                } else {
                    canvas.drawBitmap(filteredBitmap, null,
                            new Rect(0, 0,bmWidth, bmHeight), mPaint);
                }

                MasterImage.getImage().getBitmapCache().cache(filteredBitmap);
            }
        }

        return bitmap;
    }
}
