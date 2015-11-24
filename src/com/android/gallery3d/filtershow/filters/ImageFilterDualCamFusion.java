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
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.cache.BitmapCache;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.imageshow.GeometryMathUtils;
import com.android.gallery3d.filtershow.imageshow.GeometryMathUtils.GeometryHolder;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.pipeline.FilterEnvironment;
import com.android.gallery3d.filtershow.pipeline.ImagePreset;
import com.android.gallery3d.filtershow.tools.DualCameraNativeEngine;

public class ImageFilterDualCamFusion extends ImageFilter {
    private static final String TAG = ImageFilterDualCamFusion.class.getSimpleName();
    private static Toast sSegmentToast;

    private FilterDualCamFusionRepresentation mParameters;
    private Paint mPaint = new Paint();

    public ImageFilterDualCamFusion() {
        mName = "Fusion";
    }

    public void useRepresentation(FilterRepresentation representation) {
        FilterDualCamFusionRepresentation parameters = (FilterDualCamFusionRepresentation) representation;
        mParameters = parameters;
    }

    public FilterDualCamFusionRepresentation getParameters() {
        return mParameters;
    }

    public FilterRepresentation getDefaultRepresentation() {
        return new FilterDualCamFusionRepresentation();
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, int quality) {
        if (getParameters() == null) {
            return bitmap;
        }

        Point point = getParameters().getPoint();

        if(!point.equals(-1,-1)) {
            Bitmap filteredBitmap = null;
            boolean result = false;
            int orientation = MasterImage.getImage().getOrientation();
            Rect originalBounds = MasterImage.getImage().getOriginalBounds();
            int filteredW;
            int filteredH;
            int[] roiRect = new int[4];

            if(quality == FilterEnvironment.QUALITY_FINAL) {
                filteredW = originalBounds.width();
                filteredH = originalBounds.height();
            } else {
                Bitmap originalBmp = MasterImage.getImage().getOriginalBitmapHighres();
                filteredW = originalBmp.getWidth();
                filteredH = originalBmp.getHeight();

                // image is rotated
                if (orientation == ImageLoader.ORI_ROTATE_90 ||
                        orientation == ImageLoader.ORI_ROTATE_270 ||
                        orientation == ImageLoader.ORI_TRANSPOSE ||
                        orientation == ImageLoader.ORI_TRANSVERSE) {
                    int tmp = filteredW;
                    filteredW = filteredH;
                    filteredH = tmp;
                }

                // non even width or height
                if(filteredW%2 != 0 || filteredH%2 != 0) {
                    float aspect = (float)filteredH / (float)filteredW;
                    if(filteredW >= filteredH) {
                        filteredW = MasterImage.MAX_BITMAP_DIM;
                        filteredH = (int)(filteredW * aspect);
                    } else {
                        filteredH = MasterImage.MAX_BITMAP_DIM;
                        filteredW = (int)(filteredH / aspect);
                    }
                }
            }

            filteredBitmap = MasterImage.getImage().getBitmapCache().getBitmap(filteredW, filteredH, BitmapCache.FILTERS);
            filteredBitmap.setHasAlpha(true);

            result = DualCameraNativeEngine.getInstance().getForegroundImg(point.x, point.y,
                    roiRect, quality != FilterEnvironment.QUALITY_FINAL, filteredBitmap);

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

                mPaint.reset();
                mPaint.setAntiAlias(true);
                if(quality == FilterEnvironment.QUALITY_FINAL) {
                    mPaint.setFilterBitmap(true);
                    mPaint.setDither(true);
                }

                bitmap.setHasAlpha(true);

                Canvas canvas = new Canvas(bitmap);
                ImagePreset preset = getEnvironment().getImagePreset();
                int bmWidth = bitmap.getWidth();
                int bmHeight = bitmap.getHeight();
                GeometryHolder holder;
                if(preset.getDoApplyGeometry()) {
                    holder = GeometryMathUtils.unpackGeometry(preset.getGeometryFilters());
                } else {
                    holder = new GeometryHolder();
                }

                RectF roiRectF = new RectF();
                roiRectF.left = (float)roiRect[0]/(float)filteredW;
                roiRectF.top = (float)roiRect[1]/(float)filteredH;
                roiRectF.right = (float)(roiRect[0] + roiRect[2])/(float)filteredW;
                roiRectF.bottom = (float)(roiRect[1] + roiRect[3])/(float)filteredH;

                // Check for ROI cropping
                if(!FilterCropRepresentation.getNil().equals(roiRectF)) {
                    if(FilterCropRepresentation.getNil().equals(holder.crop)) {
                        // no crop filter, set crop to be roiRect
                        holder.crop.set(roiRectF);
                    } else if(roiRectF.contains(holder.crop) == false) {
                        // take smaller intersecting area between roiRect and crop rect
                        holder.crop.left = Math.max(holder.crop.left, roiRectF.left);
                        holder.crop.top = Math.max(holder.crop.top, roiRectF.top);
                        holder.crop.right = Math.min(holder.crop.right, roiRectF.right);
                        holder.crop.bottom = Math.min(holder.crop.bottom, roiRectF.bottom);
                    }
                }

                RectF crop = new RectF();
                Matrix m = GeometryMathUtils.getOriginalToScreen(holder, crop, true,
                        filteredW, filteredH, bmWidth, bmHeight);

                canvas.drawColor(0, PorterDuff.Mode.CLEAR);

                canvas.save();
                canvas.clipRect(crop);
                canvas.drawBitmap(filteredBitmap, m, mPaint);
                canvas.restore();

                MasterImage.getImage().getBitmapCache().cache(filteredBitmap);
            }
        }

        return bitmap;
    }
}
