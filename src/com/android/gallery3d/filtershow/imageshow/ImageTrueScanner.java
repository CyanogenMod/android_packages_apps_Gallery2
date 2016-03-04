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

package com.android.gallery3d.filtershow.imageshow;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import com.android.gallery3d.filtershow.crop.CropDrawingUtils;
import com.android.gallery3d.filtershow.crop.CropMath;
import com.android.gallery3d.filtershow.crop.CropObject;
import com.android.gallery3d.filtershow.editors.TrueScannerEditor;
import com.android.gallery3d.filtershow.filters.FilterCropRepresentation;
import com.android.gallery3d.filtershow.filters.TrueScannerActs;

public class ImageTrueScanner extends ImageShow {
    private static final String TAG = ImageTrueScanner.class.getSimpleName();
    TrueScannerEditor mEditor;
    private Matrix mDisplayMatrix;
    private GeometryMathUtils.GeometryHolder mGeometry;
    public final static int POINT_NUMS = 4;
    private static int[] mSegPoints = new int[POINT_NUMS*2+4];
    private boolean[] isTouched = new boolean[POINT_NUMS];
    private RectF mBitmapBound = new RectF();
    private Bitmap mBitmap;
    private Paint mSegmentPaint;
    private final static float RADIUS = 20f;
    private final static float CHECK_RADIUS = 100f;
    private int mMarginalGapX = 10;
    private int mMarginalGapY = 10;
    private boolean isPointsAcquired = false;

    public ImageTrueScanner(Context context) {
        super(context);
        init();
    }

    public ImageTrueScanner(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ImageTrueScanner(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public static int[] getDeterminedPoints() {
        return mSegPoints;
    }

    private void init() {
        mBitmap = MasterImage.getImage().getFiltersOnlyImage();
        mMarginalGapX = mBitmap.getWidth()/4;
        mMarginalGapY = mBitmap.getHeight()/4;
        mGeometry = new GeometryMathUtils.GeometryHolder();

        mSegmentPaint = new Paint();
        mSegmentPaint.setColor(Color.CYAN);
    }

    private void checkPointTouch(float x, float y) {
        for(int i=0; i<POINT_NUMS; i++) {
            if ( x > mSegPoints[i*2] - CHECK_RADIUS && x < mSegPoints[i*2] + CHECK_RADIUS
                    && y > mSegPoints[i*2+1] - CHECK_RADIUS && y < mSegPoints[i*2+1] + CHECK_RADIUS) {
                isTouched[i] = true;
            }
        }
    }

    private void moveTouch(float x, float y) {
        int i;
        for(i=0; i<POINT_NUMS; i++) {
            if(isTouched[i]) {
                mSegPoints[i*2] = (int) x;
                mSegPoints[i*2+1] = (int) y;
                break;
            }
        }
        switch(i)
        {
            case 0:
                if(mSegPoints[0] < mBitmapBound.left) mSegPoints[0] = (int)mBitmapBound.left;
                if(mSegPoints[0] >= mSegPoints[2]-mMarginalGapX)
                    mSegPoints[0] = mSegPoints[2]-mMarginalGapX;
                if(mSegPoints[1] < mBitmapBound.top) mSegPoints[1] = (int)mBitmapBound.top;
                if(mSegPoints[1] >= mSegPoints[7]-mMarginalGapY)
                    mSegPoints[1] = mSegPoints[7]-mMarginalGapY;
                break;
            case 1:
                if(mSegPoints[2] > mBitmapBound.right) mSegPoints[2] = (int)mBitmapBound.right;
                if(mSegPoints[2] <= mSegPoints[0]+mMarginalGapX)
                    mSegPoints[2] = mSegPoints[0]+mMarginalGapX;
                if(mSegPoints[3] < mBitmapBound.top) mSegPoints[3] = (int)mBitmapBound.top;
                if(mSegPoints[3] >= mSegPoints[5]-mMarginalGapY)
                    mSegPoints[3] = mSegPoints[5]-mMarginalGapY;
                break;
            case 2:
                if(mSegPoints[4] > mBitmapBound.right) mSegPoints[4] = (int)mBitmapBound.right;
                if(mSegPoints[4] <= mSegPoints[6]+mMarginalGapX)
                    mSegPoints[4] = mSegPoints[6]+mMarginalGapX;
                if(mSegPoints[5] > mBitmapBound.bottom) mSegPoints[5] = (int)mBitmapBound.bottom;
                if(mSegPoints[5] <= mSegPoints[3]+mMarginalGapY)
                    mSegPoints[5] = mSegPoints[3]+mMarginalGapY;
                break;
            case 3:
                if(mSegPoints[6] < mBitmapBound.left) mSegPoints[6] = (int)mBitmapBound.left;
                if(mSegPoints[6] >= mSegPoints[4]-mMarginalGapX)
                    mSegPoints[6] = mSegPoints[4]-mMarginalGapX;
                if(mSegPoints[7] > mBitmapBound.bottom) mSegPoints[7] = (int)mBitmapBound.bottom;
                if(mSegPoints[7] <= mSegPoints[1]+mMarginalGapY)
                    mSegPoints[7] = mSegPoints[1]+mMarginalGapY;
                break;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case (MotionEvent.ACTION_DOWN):
                checkPointTouch(x, y);
            case (MotionEvent.ACTION_MOVE):
                moveTouch(x,y);
                break;
            case (MotionEvent.ACTION_UP):
                for(int i=0; i<POINT_NUMS; i++) {
                    isTouched[i] = false;
                }
                break;
        }

        invalidate();
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
    }

    private void getBitmapBound() {
        mBitmapBound.left = 0;
        mBitmapBound.right = mBitmap.getWidth();
        mBitmapBound.top = 0;
        mBitmapBound.bottom = mBitmap.getHeight();
        mDisplayMatrix.mapRect(mBitmapBound);
    }

    private void getInitialInput() {
        int[] points = TrueScannerActs.getPoints();
        if (points == null) {
            mSegPoints[0] = (int) mBitmapBound.left;
            mSegPoints[1] = (int) mBitmapBound.top;
            mSegPoints[2] = (int) mBitmapBound.right;
            mSegPoints[3] = (int) mBitmapBound.top;
            mSegPoints[4] = (int) mBitmapBound.right;
            mSegPoints[5] = (int) mBitmapBound.bottom;
            mSegPoints[6] = (int) mBitmapBound.left;
            mSegPoints[7] = (int) mBitmapBound.bottom;
        } else {
            float xScale = mBitmapBound.width()/points[8];
            float yScale = mBitmapBound.height()/points[9];
            mSegPoints[0] = (int) (mBitmapBound.left + points[0]*xScale);
            mSegPoints[1] = (int) (mBitmapBound.top + points[1]*yScale);
            mSegPoints[2] = (int) (mBitmapBound.left + points[2]*xScale);
            mSegPoints[3] = (int) (mBitmapBound.top + points[3]*yScale);
            mSegPoints[4] = (int) (mBitmapBound.left + points[4]*xScale);
            mSegPoints[5] = (int) (mBitmapBound.top + points[5]*yScale);
            mSegPoints[6] = (int) (mBitmapBound.left + points[6]*xScale);
            mSegPoints[7] = (int) (mBitmapBound.top + points[7]*yScale);
            isPointsAcquired = true;
        }
        mSegPoints[8] = (int)mBitmapBound.width();
        mSegPoints[9] = (int)mBitmapBound.height();
        mSegPoints[10] = (int)mBitmapBound.left;
        mSegPoints[11] = (int)mBitmapBound.top;
    }

    @Override
    public void onDraw(Canvas canvas) {
        if(mBitmap == null)
            return;

        if(mDisplayMatrix == null) {
            mDisplayMatrix = GeometryMathUtils.getFullGeometryToScreenMatrix(mGeometry,
                    mBitmap.getWidth(), mBitmap.getHeight(), canvas.getWidth(), canvas.getHeight());
            getBitmapBound();
        }
        if(!isPointsAcquired) {
            getInitialInput();
        }
        canvas.drawBitmap(mBitmap, mDisplayMatrix, null);
        drawSegments(canvas);
    }

    private void drawSegments(Canvas canvas)
    {
        for(int i=0; i<POINT_NUMS; i++) {
            canvas.drawCircle(mSegPoints[i*2], mSegPoints[i*2+1], RADIUS, mSegmentPaint);
        }
        canvas.drawLine(mSegPoints[0], mSegPoints[1], mSegPoints[2], mSegPoints[3], mSegmentPaint);
        canvas.drawLine(mSegPoints[2], mSegPoints[3], mSegPoints[4], mSegPoints[5], mSegmentPaint);
        canvas.drawLine(mSegPoints[4], mSegPoints[5], mSegPoints[6], mSegPoints[7], mSegmentPaint);
        canvas.drawLine(mSegPoints[0], mSegPoints[1], mSegPoints[6], mSegPoints[7], mSegmentPaint);
    }

    public void setEditor(TrueScannerEditor editor) {
        mEditor = editor;
    }
}
