/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.android.gallery3d.app.Log;

public class ImageFilterColorBorder extends ImageFilter {
    private static final String LOGTAG = "ImageFilterColorBorder";
    private FilterColorBorderRepresentation mParameters = null;
    Paint mPaint = new Paint();
    RectF mRect = new RectF();

    public ImageFilterColorBorder() {
        mName = "Border";
        mPaint.setStyle(Paint.Style.STROKE);
    }

    public FilterRepresentation getDefaultRepresentation() {
        return new FilterColorBorderRepresentation(Color.WHITE, 4, 4);
    }

    public void useRepresentation(FilterRepresentation representation) {
        FilterColorBorderRepresentation parameters =
                (FilterColorBorderRepresentation) representation;
        mParameters = parameters;
    }

    public FilterColorBorderRepresentation getParameters() {
        return mParameters;
    }

    private void applyHelper(Canvas canvas, int w, int h) {
        if (getParameters() == null) {
            return;
        }
        mRect.set(0, 0, w, h);
        mPaint.setColor(getParameters().getColor());
        float size = getParameters().getBorderSize();
        float radius = getParameters().getBorderRadius();
        Matrix m = getOriginalToScreenMatrix(w, h);
        radius = m.mapRadius(radius);
        size = m.mapRadius(size);
        mPaint.setStrokeWidth(size);
        canvas.drawRoundRect(mRect, radius, radius, mPaint);
        mRect.set(0 - radius, -radius, w + radius, h + radius);
        canvas.drawRoundRect(mRect, 0, 0, mPaint);
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, int quality) {
        Canvas canvas = new Canvas(bitmap);
        applyHelper(canvas, bitmap.getWidth(), bitmap.getHeight());
        return bitmap;
    }

}
