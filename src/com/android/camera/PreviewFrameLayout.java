/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.camera;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.View;
import android.view.ViewStub;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import com.android.camera.ui.LayoutChangeHelper;
import com.android.camera.ui.LayoutChangeNotifier;
import com.android.camera.ui.PieRenderer;
import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;

/**
 * A layout which handles the preview aspect ratio.
 */
public class PreviewFrameLayout extends RelativeLayout implements LayoutChangeNotifier {

    private static final String TAG = "CAM_preview";

    /** A callback to be invoked when the preview frame's size changes. */
    public interface OnSizeChangedListener {
        public void onSizeChanged(int width, int height);
    }

    private double mAspectRatio;
    private View mBorder;
    private OnSizeChangedListener mListener;
    private LayoutChangeHelper mLayoutChangeHelper;
    private boolean mOrientationResize;
    private boolean mPrevOrientationResize;
    private PieRenderer mPieRenderer;

    public PreviewFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAspectRatio(4.0 / 3.0);
        mLayoutChangeHelper = new LayoutChangeHelper(this);
        mOrientationResize = false;
        mPrevOrientationResize = false;
    }

    @Override
    protected void onFinishInflate() {
        mBorder = findViewById(R.id.preview_border);
    }

     public void cameraOrientationPreviewResize(boolean orientation){
         mPrevOrientationResize = mOrientationResize;
         mOrientationResize = orientation;
    }

    public void setAspectRatio(double ratio) {
        if (ratio <= 0.0) throw new IllegalArgumentException();

        int rotation = ((WindowManager) mContext.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            ratio = 1 / ratio;
        }

        if (mAspectRatio != ratio) {
            mAspectRatio = ratio;
            requestLayout();
        }
        if(mOrientationResize != mPrevOrientationResize) {
           requestLayout();
        }

    }

    public void showBorder(boolean enabled) {
        mBorder.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
    }

    public void fadeOutBorder() {
        Util.fadeOut(mBorder);
    }

    public void setRenderer(PieRenderer renderer) {
       mPieRenderer = renderer;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int previewWidth = MeasureSpec.getSize(widthSpec);
        int previewHeight = MeasureSpec.getSize(heightSpec);
        int originalWidth = previewWidth;
        int originalHeight = previewHeight;

        if (!ApiHelper.HAS_SURFACE_TEXTURE) {
            // Get the padding of the border background.
            int hPadding = getPaddingLeft() + getPaddingRight();
            int vPadding = getPaddingTop() + getPaddingBottom();

            // Resize the preview frame with correct aspect ratio.
            previewWidth -= hPadding;
            previewHeight -= vPadding;

            boolean widthLonger = previewWidth > previewHeight;
            int longSide = (widthLonger ? previewWidth : previewHeight);
            int shortSide = (widthLonger ? previewHeight : previewWidth);
            if (longSide > shortSide * mAspectRatio) {
                longSide = (int) ((double) shortSide * mAspectRatio);
            } else {
                shortSide = (int) ((double) longSide / mAspectRatio);
            }
            if (widthLonger) {
                previewWidth = longSide;
                previewHeight = shortSide;
            } else {
                previewWidth = shortSide;
                previewHeight = longSide;
            }

            // Add the padding of the border.
            previewWidth += hPadding;
            previewHeight += vPadding;
        }

        if (mOrientationResize) {
            previewHeight = (int) (previewWidth * mAspectRatio);

            if (previewHeight > originalHeight) {
                previewWidth = (int)(((double)originalHeight / (double)previewHeight) * previewWidth);
                previewHeight = originalHeight;
            }
           /* If the preview size of frame is small e.g. less than options menu size,
            then the later appears cropped. This is because the child views use the parent
            dimensions for layout sizes. Hence for now as a workaround the preview sizes are
            updated to atleast match the options menu dimensions. This will result in a slight
            stretch of preview images */
            if(mPieRenderer != null) {
                int settingsDiameter = mPieRenderer.getDiameter();
                if(previewWidth < settingsDiameter) previewWidth = settingsDiameter;
                else if(previewHeight < settingsDiameter) previewHeight = settingsDiameter;
            }
        }

        // Ask children to follow the new preview dimension.
        super.onMeasure(MeasureSpec.makeMeasureSpec(previewWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(previewHeight, MeasureSpec.EXACTLY));
    }

    public void setOnSizeChangedListener(OnSizeChangedListener listener) {
        mListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (mListener != null) mListener.onSizeChanged(w, h);
    }

    @Override
    public void setOnLayoutChangeListener(
            LayoutChangeNotifier.Listener listener) {
        mLayoutChangeHelper.setOnLayoutChangeListener(listener);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mLayoutChangeHelper.onLayout(changed, l, t, r, b);
    }
}
