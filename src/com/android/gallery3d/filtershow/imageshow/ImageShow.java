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

package com.android.gallery3d.filtershow.imageshow;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.NinePatchDrawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.filters.FilterMirrorRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.pipeline.ImagePreset;
import com.android.gallery3d.filtershow.tools.SaveImage;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

public class ImageShow extends View implements OnGestureListener,
        ScaleGestureDetector.OnScaleGestureListener,
        OnDoubleTapListener {

    private static final String LOGTAG = "ImageShow";
    private static final boolean ENABLE_ZOOMED_COMPARISON = false;

    protected Paint mPaint = new Paint();
    protected int mTextSize;
    protected int mTextPadding;

    protected int mBackgroundColor;

    private GestureDetector mGestureDetector = null;
    private ScaleGestureDetector mScaleGestureDetector = null;

    protected Rect mImageBounds = new Rect();
    private boolean mOriginalDisabled = false;
    private boolean mTouchShowOriginal = false;
    private long mTouchShowOriginalDate = 0;
    private final long mTouchShowOriginalDelayMin = 200; // 200ms
    private int mShowOriginalDirection = 0;
    private static int UNVEIL_HORIZONTAL = 1;
    private static int UNVEIL_VERTICAL = 2;

    private NinePatchDrawable mShadow = null;
    private Rect mShadowBounds = new Rect();
    private int mShadowMargin = 15; // not scaled, fixed in the asset
    private boolean mShadowDrawn = false;

    private Point mTouchDown = new Point();
    private Point mTouch = new Point();
    private boolean mFinishedScalingOperation = false;

    private int mOriginalTextMargin;
    private int mOriginalTextSize;
    private String mOriginalText;
    private boolean mZoomIn = false;
    Point mOriginalTranslation = new Point();
    float mOriginalScale;
    float mStartFocusX, mStartFocusY;
    private enum InteractionMode {
        NONE,
        SCALE,
        MOVE
    }
    InteractionMode mInteractionMode = InteractionMode.NONE;

    private static Bitmap sMask;
    private Paint mMaskPaint = new Paint();
    private Matrix mShaderMatrix = new Matrix();

    private static Bitmap convertToAlphaMask(Bitmap b) {
        Bitmap a = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(a);
        c.drawBitmap(b, 0.0f, 0.0f, null);
        return a;
    }

    private static Shader createShader(Bitmap b) {
        return new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    }

    private FilterShowActivity mActivity = null;

    public FilterShowActivity getActivity() {
        return mActivity;
    }

    public boolean hasModifications() {
        return MasterImage.getImage().hasModifications();
    }

    public void resetParameter() {
        // TODO: implement reset
    }

    public void onNewValue(int parameter) {
        invalidate();
    }

    public ImageShow(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setupImageShow(context);
    }

    public ImageShow(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupImageShow(context);

    }

    public ImageShow(Context context) {
        super(context);
        setupImageShow(context);
    }

    private void setupImageShow(Context context) {
        Resources res = context.getResources();
        mTextSize = res.getDimensionPixelSize(R.dimen.photoeditor_text_size);
        mTextPadding = res.getDimensionPixelSize(R.dimen.photoeditor_text_padding);
        mOriginalTextMargin = res.getDimensionPixelSize(R.dimen.photoeditor_original_text_margin);
        mOriginalTextSize = res.getDimensionPixelSize(R.dimen.photoeditor_original_text_size);
        mBackgroundColor = res.getColor(R.color.background_screen);
        mOriginalText = res.getString(R.string.original_picture_text);
        mShadow = (NinePatchDrawable) res.getDrawable(R.drawable.geometry_shadow);
        setupGestureDetector(context);
        mActivity = (FilterShowActivity) context;
        if (sMask == null) {
            Bitmap mask = BitmapFactory.decodeResource(res, R.drawable.spot_mask);
            sMask = convertToAlphaMask(mask);
        }
    }

    public void attach() {
        MasterImage.getImage().addObserver(this);
        bindAsImageLoadListener();
    }

    public void detach() {
        MasterImage.getImage().removeObserver(this);
        mMaskPaint.reset();
    }

    public void setupGestureDetector(Context context) {
        mGestureDetector = new GestureDetector(context, this);
        mScaleGestureDetector = new ScaleGestureDetector(context, this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(parentWidth, parentHeight);
    }

    public ImageFilter getCurrentFilter() {
        return MasterImage.getImage().getCurrentFilter();
    }

    /* consider moving the following 2 methods into a subclass */
    /**
     * This function calculates a Image to Screen Transformation matrix
     *
     * @param reflectRotation set true if you want the rotation encoded
     * @return Image to Screen transformation matrix
     */
    protected Matrix getImageToScreenMatrix(boolean reflectRotation) {
        MasterImage master = MasterImage.getImage();
        if (master.getOriginalBounds() == null) {
            return new Matrix();
        }
        Matrix m = GeometryMathUtils.getImageToScreenMatrix(master.getPreset().getGeometryFilters(),
                reflectRotation, master.getOriginalBounds(), getWidth(), getHeight());
        Point translate = master.getTranslation();
        float scaleFactor = master.getScaleFactor();
        m.postTranslate(translate.x, translate.y);
        m.postScale(scaleFactor, scaleFactor, getWidth() / 2.0f, getHeight() / 2.0f);
        return m;
    }

    /**
     * This function calculates a to Screen Image Transformation matrix
     *
     * @param reflectRotation set true if you want the rotation encoded
     * @return Screen to Image transformation matrix
     */
    protected Matrix getScreenToImageMatrix(boolean reflectRotation) {
        Matrix m = getImageToScreenMatrix(reflectRotation);
        Matrix invert = new Matrix();
        m.invert(invert);
        return invert;
    }

    public ImagePreset getImagePreset() {
        return MasterImage.getImage().getPreset();
    }

    @Override
    public void onDraw(Canvas canvas) {
        MasterImage.getImage().setImageShowSize(
                getWidth() - 2*mShadowMargin,
                getHeight() - 2*mShadowMargin);

        float cx = canvas.getWidth()/2.0f;
        float cy = canvas.getHeight()/2.0f;
        float scaleFactor = MasterImage.getImage().getScaleFactor();
        Point translation = MasterImage.getImage().getTranslation();

        canvas.save();

        mShadowDrawn = false;

        canvas.save();
        // TODO: center scale on gesture
        canvas.scale(scaleFactor, scaleFactor, cx, cy);
        canvas.translate(translation.x, translation.y);
        Bitmap highresPreview = MasterImage.getImage().getHighresImage();

        boolean isDoingNewLookAnimation = MasterImage.getImage().onGoingNewLookAnimation();

        if (!isDoingNewLookAnimation && highresPreview != null) {
            drawImage(canvas, highresPreview, true);
        } else {
            drawImage(canvas, getFilteredImage(), true);
        }
        canvas.restore();

        Bitmap partialPreview = MasterImage.getImage().getPartialImage();
        if (!isDoingNewLookAnimation && partialPreview != null) {
            canvas.save();
            Rect originalBounds = MasterImage.getImage().getOriginalBounds();
            Collection<FilterRepresentation> geo = MasterImage.getImage().getPreset()
                    .getGeometryFilters();

            Matrix compensation = GeometryMathUtils.getPartialToScreenMatrix(geo,
                    originalBounds, getWidth(), getHeight(),
                    partialPreview.getWidth(), partialPreview.getHeight());
            canvas.drawBitmap(partialPreview, compensation, null);
            canvas.restore();
        }

        canvas.save();
        canvas.scale(scaleFactor, scaleFactor, cx, cy);
        canvas.translate(translation.x, translation.y);
        drawPartialImage(canvas, getGeometryOnlyImage());
        canvas.restore();

        canvas.restore();
    }

    public void resetImageCaches(ImageShow caller) {
        MasterImage.getImage().invalidatePreview();
    }

    public Bitmap getFiltersOnlyImage() {
        return MasterImage.getImage().getFiltersOnlyImage();
    }

    public Bitmap getGeometryOnlyImage() {
        return MasterImage.getImage().getGeometryOnlyImage();
    }

    public Bitmap getFilteredImage() {
        return MasterImage.getImage().getFilteredImage();
    }

    public void drawImage(Canvas canvas, Bitmap image, boolean updateBounds) {
        if (image == null) {
            return;
        }

        Rect d = computeImageBounds(image);

        if (updateBounds) {
            mImageBounds = d;
        }

        float centerX = mShadowMargin + (getWidth() - 2 * mShadowMargin) / 2;
        float centerY = mShadowMargin + (getHeight() - 2 * mShadowMargin) / 2;

        MasterImage master = MasterImage.getImage();
        canvas.save();
        if (master.onGoingNewLookAnimation()) {
            if (master.getCurrentLookAnimation()
                    == MasterImage.CIRCLE_ANIMATION
                    && MasterImage.getImage().getPreviousImage() != null) {
                float maskScale = MasterImage.getImage().getMaskScale();
                if (maskScale > 1.0f) {
                    float maskW = sMask.getWidth() / 2.0f;
                    float maskH = sMask.getHeight() / 2.0f;
                    float x = centerX - maskW * maskScale;
                    float y = centerY - maskH * maskScale;

                    // Prepare the shader
                    mShaderMatrix.reset();
                    mShaderMatrix.setScale(1.0f / maskScale, 1.0f / maskScale);
                    mShaderMatrix.preTranslate(-x + d.left, -y + d.top);
                    float scaleImageX = d.width() / (float) image.getWidth();
                    float scaleImageY = d.height() / (float) image.getHeight();
                    mShaderMatrix.preScale(scaleImageX, scaleImageY);
                    mMaskPaint.reset();
                    mMaskPaint.setShader(createShader(image));
                    mMaskPaint.getShader().setLocalMatrix(mShaderMatrix);

                    drawImage(canvas, MasterImage.getImage().getPreviousImage());
                    canvas.translate(x, y);
                    canvas.scale(maskScale, maskScale);
                    canvas.drawBitmap(sMask, 0, 0, mMaskPaint);
                } else {
                    drawImage(canvas, image);
                }
            } else if (master.getCurrentLookAnimation()
                    == MasterImage.ROTATE_ANIMATION) {
                Rect d2 = computeImageBounds(master.getPreviousImage());
                float finalScale = d.width() / (float) d2.height();
                finalScale = (1.0f * (1.0f - master.getAnimFraction()))
                        + (finalScale * master.getAnimFraction());
                canvas.rotate(master.getAnimRotationValue(), centerX, centerY);
                canvas.scale(finalScale, finalScale, centerX, centerY);
                drawImage(canvas, master.getPreviousImage());
            } else if (master.getCurrentLookAnimation()
                    == MasterImage.MIRROR_ANIMATION) {
                if (master.getCurrentFilterRepresentation()
                        instanceof FilterMirrorRepresentation) {
                    FilterMirrorRepresentation rep =
                            (FilterMirrorRepresentation) master.getCurrentFilterRepresentation();

                    ImagePreset preset = master.getPreset();
                    ArrayList<FilterRepresentation> geometry =
                            (ArrayList<FilterRepresentation>) preset.getGeometryFilters();
                    GeometryMathUtils.GeometryHolder holder = null;
                    holder = GeometryMathUtils.unpackGeometry(geometry);

                    if (holder.rotation.value() == 90 || holder.rotation.value() == 270) {
                        if (rep.isHorizontal() && !rep.isVertical()) {
                            canvas.scale(1, master.getAnimRotationValue(), centerX, centerY);
                        } else if (rep.isVertical() && !rep.isHorizontal()) {
                            canvas.scale(1, master.getAnimRotationValue(), centerX, centerY);
                        } else if (rep.isHorizontal() && rep.isVertical()) {
                            canvas.scale(master.getAnimRotationValue(), 1, centerX, centerY);
                        } else {
                            canvas.scale(master.getAnimRotationValue(), 1, centerX, centerY);
                        }
                    } else {
                        if (rep.isHorizontal() && !rep.isVertical()) {
                            canvas.scale(master.getAnimRotationValue(), 1, centerX, centerY);
                        } else if (rep.isVertical() && !rep.isHorizontal()) {
                            canvas.scale(master.getAnimRotationValue(), 1, centerX, centerY);
                        } else  if (rep.isHorizontal() && rep.isVertical()) {
                            canvas.scale(1, master.getAnimRotationValue(), centerX, centerY);
                        } else {
                            canvas.scale(1, master.getAnimRotationValue(), centerX, centerY);
                        }
                    }
                }
                drawImage(canvas, master.getPreviousImage());
            }
        } else {
            drawImage(canvas, image);
        }
        canvas.restore();
    }

    private void drawImage(Canvas canvas, Bitmap image) {
        Rect d = computeImageBounds(image);
        float scaleImageX = d.width() / (float) image.getWidth();
        float scaleImageY = d.height() / (float) image.getHeight();
        Matrix imageMatrix = new Matrix();
        imageMatrix.postScale(scaleImageX, scaleImageY);
        imageMatrix.postTranslate(d.left, d.top);
        drawShadow(canvas, d);
        canvas.clipRect(d);
        canvas.drawBitmap(image, imageMatrix, mPaint);
    }

    private Rect computeImageBounds(Bitmap image) {
        float scale = GeometryMathUtils.scale(image.getWidth(), image.getHeight(),
                getWidth(), getHeight());

        float w = image.getWidth() * scale;
        float h = image.getHeight() * scale;
        float ty = (getHeight() - h) / 2.0f;
        float tx = (getWidth() - w) / 2.0f;
        return new Rect((int) tx + mShadowMargin,
                (int) ty + mShadowMargin,
                (int) (w + tx) - mShadowMargin,
                (int) (h + ty) - mShadowMargin);
    }

    private void drawShadow(Canvas canvas, Rect d) {
        if (!mShadowDrawn) {
            mShadowBounds.set(d.left - mShadowMargin, d.top - mShadowMargin,
                    d.right + mShadowMargin, d.bottom + mShadowMargin);
            mShadow.setBounds(mShadowBounds);
            mShadow.draw(canvas);
            mShadowDrawn = true;
        }
    }

    public void drawPartialImage(Canvas canvas, Bitmap image) {
        boolean showsOriginal = MasterImage.getImage().showsOriginal();
        if (!showsOriginal && !mTouchShowOriginal)
            return;
        canvas.save();
        if (image != null) {
            if (mShowOriginalDirection == 0) {
                if (Math.abs(mTouch.y - mTouchDown.y) > Math.abs(mTouch.x - mTouchDown.x)) {
                    mShowOriginalDirection = UNVEIL_VERTICAL;
                } else {
                    mShowOriginalDirection = UNVEIL_HORIZONTAL;
                }
            }

            int px = 0;
            int py = 0;
            if (mShowOriginalDirection == UNVEIL_VERTICAL) {
                px = mImageBounds.width();
                py = mTouch.y - mImageBounds.top;
            } else {
                px = mTouch.x - mImageBounds.left;
                py = mImageBounds.height();
                if (showsOriginal) {
                    px = mImageBounds.width();
                }
            }

            Rect d = new Rect(mImageBounds.left, mImageBounds.top,
                    mImageBounds.left + px, mImageBounds.top + py);
            canvas.clipRect(d);
            drawImage(canvas, image, false);
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setStrokeWidth(3);

            if (mShowOriginalDirection == UNVEIL_VERTICAL) {
                canvas.drawLine(mImageBounds.left, mTouch.y,
                        mImageBounds.right, mTouch.y, paint);
            } else {
                canvas.drawLine(mTouch.x, mImageBounds.top,
                        mTouch.x, mImageBounds.bottom, paint);
            }

            Rect bounds = new Rect();
            paint.setAntiAlias(true);
            paint.setTextSize(mOriginalTextSize);
            paint.getTextBounds(mOriginalText, 0, mOriginalText.length(), bounds);
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            canvas.drawText(mOriginalText, mImageBounds.left + mOriginalTextMargin,
                    mImageBounds.top + bounds.height() + mOriginalTextMargin, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(1);
            paint.setColor(Color.WHITE);
            canvas.drawText(mOriginalText, mImageBounds.left + mOriginalTextMargin,
                    mImageBounds.top + bounds.height() + mOriginalTextMargin, paint);
        }
        canvas.restore();
    }

    public void bindAsImageLoadListener() {
        MasterImage.getImage().addListener(this);
    }

    public void updateImage() {
        invalidate();
    }

    public void imageLoaded() {
        updateImage();
    }

    public void saveImage(FilterShowActivity filterShowActivity, File file) {
        SaveImage.saveImage(getImagePreset(), filterShowActivity, file);
    }


    public boolean scaleInProgress() {
        return mScaleGestureDetector.isInProgress();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        int action = event.getAction();
        action = action & MotionEvent.ACTION_MASK;

        mGestureDetector.onTouchEvent(event);
        boolean scaleInProgress = scaleInProgress();
        mScaleGestureDetector.onTouchEvent(event);
        if (mInteractionMode == InteractionMode.SCALE) {
            return true;
        }
        if (!scaleInProgress() && scaleInProgress) {
            // If we were scaling, the scale will stop but we will
            // still issue an ACTION_UP. Let the subclasses know.
            mFinishedScalingOperation = true;
        }

        int ex = (int) event.getX();
        int ey = (int) event.getY();
        if (action == MotionEvent.ACTION_DOWN) {
            mInteractionMode = InteractionMode.MOVE;
            mTouchDown.x = ex;
            mTouchDown.y = ey;
            mTouchShowOriginalDate = System.currentTimeMillis();
            mShowOriginalDirection = 0;
            MasterImage.getImage().setOriginalTranslation(MasterImage.getImage().getTranslation());
        }

        if (action == MotionEvent.ACTION_MOVE && mInteractionMode == InteractionMode.MOVE) {
            mTouch.x = ex;
            mTouch.y = ey;

            float scaleFactor = MasterImage.getImage().getScaleFactor();
            if (scaleFactor > 1 && (!ENABLE_ZOOMED_COMPARISON || event.getPointerCount() == 2)) {
                float translateX = (mTouch.x - mTouchDown.x) / scaleFactor;
                float translateY = (mTouch.y - mTouchDown.y) / scaleFactor;
                Point originalTranslation = MasterImage.getImage().getOriginalTranslation();
                Point translation = MasterImage.getImage().getTranslation();
                translation.x = (int) (originalTranslation.x + translateX);
                translation.y = (int) (originalTranslation.y + translateY);
                constrainTranslation(translation, scaleFactor);
                MasterImage.getImage().setTranslation(translation);
                mTouchShowOriginal = false;
            } else if (enableComparison() && !mOriginalDisabled
                    && (System.currentTimeMillis() - mTouchShowOriginalDate
                            > mTouchShowOriginalDelayMin)
                    && event.getPointerCount() == 1) {
                mTouchShowOriginal = true;
            }
        }

        if (action == MotionEvent.ACTION_UP) {
            mInteractionMode = InteractionMode.NONE;
            mTouchShowOriginal = false;
            mTouchDown.x = 0;
            mTouchDown.y = 0;
            mTouch.x = 0;
            mTouch.y = 0;
            if (MasterImage.getImage().getScaleFactor() <= 1) {
                MasterImage.getImage().setScaleFactor(1);
                MasterImage.getImage().resetTranslation();
            }
        }
        invalidate();
        return true;
    }

    protected boolean enableComparison() {
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent arg0) {
        mZoomIn = !mZoomIn;
        float scale = 1.0f;
        if (mZoomIn) {
            scale = MasterImage.getImage().getMaxScaleFactor();
        }
        if (scale != MasterImage.getImage().getScaleFactor()) {
            MasterImage.getImage().setScaleFactor(scale);
            float translateX = (getWidth() / 2 - arg0.getX());
            float translateY = (getHeight() / 2 - arg0.getY());
            Point translation = MasterImage.getImage().getTranslation();
            translation.x = (int) (mOriginalTranslation.x + translateX);
            translation.y = (int) (mOriginalTranslation.y + translateY);
            constrainTranslation(translation, scale);
            MasterImage.getImage().setTranslation(translation);
            invalidate();
        }
        return true;
    }

    private void constrainTranslation(Point translation, float scale) {
        float maxTranslationX = getWidth() / scale;
        float maxTranslationY = getHeight() / scale;
        if (Math.abs(translation.x) > maxTranslationX) {
            translation.x = (int) (Math.signum(translation.x) *
                    maxTranslationX);
            if (Math.abs(translation.y) > maxTranslationY) {
                translation.y = (int) (Math.signum(translation.y) *
                        maxTranslationY);
            }

        }
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent arg0) {
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent arg0) {
        return false;
    }

    @Override
    public boolean onDown(MotionEvent arg0) {
        return false;
    }

    @Override
    public boolean onFling(MotionEvent startEvent, MotionEvent endEvent, float arg2, float arg3) {
        if (mActivity == null) {
            return false;
        }
        if (endEvent.getPointerCount() == 2) {
            return false;
        }
        return true;
    }

    @Override
    public void onLongPress(MotionEvent arg0) {
    }

    @Override
    public boolean onScroll(MotionEvent arg0, MotionEvent arg1, float arg2, float arg3) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent arg0) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent arg0) {
        return false;
    }

    public boolean useUtilityPanel() {
        return false;
    }

    public void openUtilityPanel(final LinearLayout accessoryViewList) {
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        MasterImage img = MasterImage.getImage();
        float scaleFactor = img.getScaleFactor();

        scaleFactor = scaleFactor * detector.getScaleFactor();
        if (scaleFactor > MasterImage.getImage().getMaxScaleFactor()) {
            scaleFactor = MasterImage.getImage().getMaxScaleFactor();
        }
        if (scaleFactor < 0.5) {
            scaleFactor = 0.5f;
        }
        MasterImage.getImage().setScaleFactor(scaleFactor);
        scaleFactor = img.getScaleFactor();
        float focusx = detector.getFocusX();
        float focusy = detector.getFocusY();
        float translateX = (focusx - mStartFocusX) / scaleFactor;
        float translateY = (focusy - mStartFocusY) / scaleFactor;
        Point translation = MasterImage.getImage().getTranslation();
        translation.x = (int) (mOriginalTranslation.x + translateX);
        translation.y = (int) (mOriginalTranslation.y + translateY);
        constrainTranslation(translation, scaleFactor);
        MasterImage.getImage().setTranslation(translation);

        invalidate();
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        Point pos = MasterImage.getImage().getTranslation();
        mOriginalTranslation.x = pos.x;
        mOriginalTranslation.y = pos.y;
        mOriginalScale = MasterImage.getImage().getScaleFactor();
        mStartFocusX = detector.getFocusX();
        mStartFocusY = detector.getFocusY();
        mInteractionMode = InteractionMode.SCALE;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        mInteractionMode = InteractionMode.NONE;
        if (MasterImage.getImage().getScaleFactor() < 1) {
            MasterImage.getImage().setScaleFactor(1);
            invalidate();
        }
    }

    public boolean didFinishScalingOperation() {
        if (mFinishedScalingOperation) {
            mFinishedScalingOperation = false;
            return true;
        }
        return false;
    }

}
