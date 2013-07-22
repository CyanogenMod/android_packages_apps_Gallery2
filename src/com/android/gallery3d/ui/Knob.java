/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *       * Redistributions in binary form must reproduce the above
 *         copyright notice, this list of conditions and the following
 *         disclaimer in the documentation and/or other materials provided
 *         with the distribution.
 *       * Neither the name of The Linux Foundation nor the names of its
 *         contributors may be used to endorse or promote products derived
 *         from this software without specific prior written permission.
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
package com.android.gallery3d.ui;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.lang.Math;

import com.android.gallery3d.R;

public class Knob extends FrameLayout {
    private final int STROKE_WIDTH = 6;

    public interface OnKnobChangeListener {
        void onValueChanged(Knob knob, int value, boolean fromUser);
        void onStartTrackingTouch(Knob knob);
        void onStopTrackingTouch(Knob knob);
        boolean onSwitchChanged(Knob knob, boolean on);
    }

    private OnKnobChangeListener mOnKnobChangeListener = null;

    private float mProgress = 0.0f;
    private int mMax = 100;
    private boolean mOn = false;
    private boolean mEnabled = false;

    private int mHighlightColor;
    private int mLowlightColor;
    private int mDisabledColor;

    private final Paint mPaint;

    private final TextView mLabelTV;
    private final TextView mProgressTV;

    private final ImageView mKnobOn;
    private final ImageView mKnobOff;

    private float mLastX;
    private float mLastY;
    private boolean mMoved;

    private int mWidth = 0;
    private int mIndicatorWidth = 0;

    private RectF mRectF;

    public Knob(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Knob, 0, 0);

        String label;
        int background, foreground;
        try {
            label = a.getString(R.styleable.Knob_label);
            background = a.getResourceId(R.styleable.Knob_background, R.drawable.knob_bg);
            foreground = a.getResourceId(R.styleable.Knob_foreground, R.drawable.knob);
        } finally {
            a.recycle();
        }

        LayoutInflater li = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        li.inflate(R.layout.knob, this, true);

        Resources res = getResources();
        mHighlightColor = res.getColor(R.color.highlight);
        mLowlightColor = res.getColor(R.color.lowlight);
        mDisabledColor = res.getColor(R.color.disabled);

        setBackgroundResource(background);
        ((ImageView) findViewById(R.id.knob_foreground)).setImageResource(foreground);

        mLabelTV = (TextView) findViewById(R.id.knob_label);
        mLabelTV.setText(label);
        mProgressTV = (TextView) findViewById(R.id.knob_value);

        mKnobOn = (ImageView) findViewById(R.id.knob_toggle_on);
        mKnobOff = (ImageView) findViewById(R.id.knob_toggle_off);

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(mHighlightColor);
        mPaint.setStrokeWidth(STROKE_WIDTH);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStyle(Paint.Style.STROKE);

        setWillNotDraw(false);
    }

    public Knob(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Knob(Context context) {
        this(context, null);
    }

    public void setOnKnobChangeListener(OnKnobChangeListener l) {
        mOnKnobChangeListener = l;
    }

    public void setValue(int value) {
        if (mMax != 0) {
            setProgress(((float) value) / mMax);
        }
    }

    public int getValue() {
        return (int) (mProgress * mMax);
    }

    public void setProgress(float progress) {
        setProgress(progress, false);
    }

    private void setProgress(float progress, boolean fromUser) {
        if (progress > 1.0f) {
            progress = 1.0f;
        }
        if (progress < 0.0f) {
            progress = 0.0f;
        }
        mProgress = progress;

        invalidate();

        if (mOnKnobChangeListener != null) {
            mOnKnobChangeListener.onValueChanged(this, (int) (progress * mMax), fromUser);
        }
    }

    public void setMax(int max) {
        mMax = max;
    }

    public float getProgress() {
        return mProgress;
    }

    private void drawIndicator() {
        float r = mWidth / 2 - 25;
        ImageView view = mOn ? mKnobOn : mKnobOff;
        view.setTranslationX((float) Math.sin(mProgress * 2 * Math.PI) * r - mIndicatorWidth / 2);
        view.setTranslationY((float) -Math.cos(mProgress * 2 * Math.PI) * r - mIndicatorWidth / 2);
    }

    @Override
    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        setOn(enabled);
    }

    public void setOn(boolean on) {
        if (on != mOn) {
            mOn = on;
        }
        on = on && mEnabled;
        mLabelTV.setTextColor(on ? mHighlightColor : mDisabledColor);
        mProgressTV.setTextColor(on ? mHighlightColor : mDisabledColor);
        mPaint.setColor(on ? mHighlightColor : mDisabledColor);
        mKnobOn.setVisibility(on ? View.VISIBLE : View.GONE);
        mKnobOff.setVisibility(on ? View.GONE : View.VISIBLE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mProgressTV.setText((int)(mProgress * 100) + "%");
        drawIndicator();
        canvas.drawArc(mRectF, -90, mProgress * 360, false, mPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        mWidth = w;
        mIndicatorWidth = mKnobOn.getWidth();

        mRectF = new RectF(STROKE_WIDTH, STROKE_WIDTH,
                mWidth - STROKE_WIDTH, mWidth - STROKE_WIDTH);

        mProgressTV.setTextSize(TypedValue.COMPLEX_UNIT_PX, w * 0.16f);
        mProgressTV.setPadding(0, (int) (w * 0.33), 0, 0);
        mProgressTV.setVisibility(View.VISIBLE);
        mLabelTV.setTextSize(TypedValue.COMPLEX_UNIT_PX, w * 0.12f);
        mLabelTV.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (mOn) {
                    mLastX = event.getX();
                    mLastY = event.getY();
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mOn) {
                    float x = event.getX();
                    float y = event.getY();
                    float center = mWidth / 2;
                    if (mMoved || (x - center) * (x - center) + (y - center) * (y - center)
                            > center * center / 4) {
                        float delta = getDelta(x, y);
                        setProgress(mProgress + delta / 360, true);
                        if (!mMoved && mOnKnobChangeListener != null) {
                            mOnKnobChangeListener.onStartTrackingTouch(this);
                        }
                        mMoved = true;
                    }
                    mLastX = x;
                    mLastY = y;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mMoved) {
                    if (mOnKnobChangeListener != null) {
                        mOnKnobChangeListener.onStopTrackingTouch(this);
                    }
                } else {
                    if (mOnKnobChangeListener == null
                            || mOnKnobChangeListener.onSwitchChanged(this, !mOn)) {
                        if (mEnabled) {
                            setOn(!mOn);
                            invalidate();
                        }
                    }
                }
                mMoved = false;
                break;
            default:
                break;
        }
        return true;
    }

    private float getDelta(float x, float y) {
        float angle = angle(x, y);
        float oldAngle = angle(mLastX, mLastY);
        float delta = angle - oldAngle;
        if (delta >= 180.0f) {
            delta = -oldAngle;
        } else if (delta <= -180.0f) {
            delta = 360 - oldAngle;
        }
        return delta;
    }

    private float angle(float x, float y) {
        float center = mWidth / 2.0f;
        x -= center;
        y -= center;

        if (x == 0.0f) {
            if (y > 0.0f) {
                return 180.0f;
            } else {
                return 0.0f;
            }
        }

        float angle = (float) (Math.atan(y / x) / Math.PI * 180.0);
        if (x > 0.0f) {
            angle += 90;
        } else {
            angle += 270;
        }
        return angle;
    }
}
