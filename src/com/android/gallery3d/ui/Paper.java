/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.ui;

import android.graphics.Rect;
import android.opengl.Matrix;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.android.gallery3d.common.Utils;

// This class does the overscroll effect.
class Paper {
    @SuppressWarnings("unused")
    private static final String TAG = "Paper";
    private static final int ROTATE_FACTOR = 4;
    private EdgeAnimation mAnimationBegin = new EdgeAnimation();
    private EdgeAnimation mAnimationEnd = new EdgeAnimation();
    private int mWidth;
    private int mHeight;
    private float[] mMatrix = new float[16];

    private final boolean mIsWide;

    public Paper(boolean wide) {
        mIsWide = wide;
    }

    public void overScroll(float distance) {
        distance /= mWidth;  // make it relative to width
        if (distance < 0) {
            mAnimationBegin.onPull(-distance);
        } else {
            mAnimationEnd.onPull(distance);
        }
    }

    public void edgeReached(float velocity) {
        velocity /= mWidth;  // make it relative to width
        if (velocity < 0) {
            mAnimationEnd.onAbsorb(-velocity);
        } else {
            mAnimationBegin.onAbsorb(velocity);
        }
    }

    public void onRelease() {
        mAnimationBegin.onRelease();
        mAnimationEnd.onRelease();
    }

    public boolean advanceAnimation() {
        // Note that we use "|" because we want both animations get updated.
        return mAnimationBegin.update() | mAnimationEnd.update();
    }

    public void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public float[] getTransform(Rect rect, float scroll) {
        Log.d(TAG, rect.toString());
        float start = mAnimationBegin.getValue();
        float end = mAnimationEnd.getValue();
        int center = 0;
        int screenWidth = mWidth;
        int rotateX = 0;
        int rotateY = 0;
        final boolean wide = mIsWide;
        if (wide) {
            center = rect.centerX();
            rotateY = 1;
        } else {
            center = rect.centerY();
            rotateX = 1;
            screenWidth = mHeight;
        }
        float screen = center - scroll;
        // We linearly interpolate the value [start, end] for the screen
        // range int [-1/4, 5/4]*screenWidth. So if part of the thumbnail is outside
        // the screen, we still get some transform.
        float x = screen + screenWidth / 4;
        int range = 3 * screenWidth / 2;
        float t = ((range - x) * start - x * end) / range;

        // compress t to the range (-1, 1) by the function
        // f(t) = (1 / (1 + e^-t) - 0.5) * 2
        // then multiply by 90 to make the range (-45, 45)
        float degrees =
                (1 / (1 + (float) Math.exp(-t * ROTATE_FACTOR)) - 0.5f) * 2 * -45;
        Matrix.setIdentityM(mMatrix, 0);
        Matrix.translateM(mMatrix, 0, mMatrix, 0, rect.centerX(), rect.centerY(), 0);
        Matrix.rotateM(mMatrix, 0, degrees, rotateX, rotateY, 0);
        Matrix.translateM(mMatrix, 0, mMatrix, 0, -rect.width() / 2, -rect.height() / 2, 0);

        return mMatrix;
    }
}

// This class follows the structure of frameworks's EdgeEffect class.
class EdgeAnimation {
    @SuppressWarnings("unused")
    private static final String TAG = "EdgeAnimation";

    private static final int STATE_IDLE = 0;
    private static final int STATE_PULL = 1;
    private static final int STATE_ABSORB = 2;
    private static final int STATE_RELEASE = 3;

    // Time it will take the effect to fully done in ms
    private static final int ABSORB_TIME = 200;
    private static final int RELEASE_TIME = 500;

    private static final float VELOCITY_FACTOR = 0.1f;

    private final Interpolator mInterpolator;

    private int mState;
    private float mValue;

    private float mValueStart;
    private float mValueFinish;
    private long mStartTime;
    private long mDuration;

    public EdgeAnimation() {
        mInterpolator = new DecelerateInterpolator();
        mState = STATE_IDLE;
    }

    private void startAnimation(float start, float finish, long duration,
            int newState) {
        mValueStart = start;
        mValueFinish = finish;
        mDuration = duration;
        mStartTime = now();
        mState = newState;
    }

    // The deltaDistance's magnitude is in the range of -1 (no change) to 1.
    // The value 1 is the full length of the view. Negative values means the
    // movement is in the opposite direction.
    public void onPull(float deltaDistance) {
        if (mState == STATE_ABSORB) return;
        mValue = Utils.clamp(mValue + deltaDistance, -1.0f, 1.0f);
        mState = STATE_PULL;
    }

    public void onRelease() {
        if (mState == STATE_IDLE || mState == STATE_ABSORB) return;
        startAnimation(mValue, 0, RELEASE_TIME, STATE_RELEASE);
    }

    public void onAbsorb(float velocity) {
        float finish = Utils.clamp(mValue + velocity * VELOCITY_FACTOR,
                -1.0f, 1.0f);
        startAnimation(mValue, finish, ABSORB_TIME, STATE_ABSORB);
    }

    public boolean update() {
        if (mState == STATE_IDLE) return false;
        if (mState == STATE_PULL) return true;

        float t = Utils.clamp((float)(now() - mStartTime) / mDuration, 0.0f, 1.0f);
        /* Use linear interpolation for absorb, quadratic for others */
        float interp = (mState == STATE_ABSORB)
                ? t : mInterpolator.getInterpolation(t);

        mValue = mValueStart + (mValueFinish - mValueStart) * interp;

        if (t >= 1.0f) {
            switch (mState) {
                case STATE_ABSORB:
                    startAnimation(mValue, 0, RELEASE_TIME, STATE_RELEASE);
                    break;
                case STATE_RELEASE:
                    mState = STATE_IDLE;
                    break;
            }
        }

        return true;
    }

    public float getValue() {
        return mValue;
    }

    private long now() {
        return AnimationTime.get();
    }
}
