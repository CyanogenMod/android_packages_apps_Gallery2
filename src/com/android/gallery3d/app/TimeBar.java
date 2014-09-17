/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.gallery3d.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;

import java.util.Locale;

/**
 * The time bar view, which includes the current and total time, the progress
 * bar, and the scrubber.
 */
public class TimeBar extends View {

    public interface Listener {
        void onScrubbingStart();

        void onScrubbingMove(int time);

        void onScrubbingEnd(int time, int start, int end);
    }

    // Padding around the scrubber to increase its touch target
    private static final int SCRUBBER_PADDING_IN_DP = 10;

    // The total padding, top plus bottom
    private static final int V_PADDING_IN_DP = 30;

    private static final int TEXT_SIZE_IN_DP = 14;

    private static final String TAG = "Gallery3D/TimeBar";
    private static final boolean LOG = false;
    public static final int UNKNOWN = -1;

    protected final Listener mListener;

    // the bars we use for displaying the progress
    protected final Rect mProgressBar;
    protected final Rect mPlayedBar;

    protected final Paint mProgressPaint;
    protected final Paint mPlayedPaint;
    protected final Paint mTimeTextPaint;

    protected final Bitmap mScrubber;
    protected int mScrubberPadding; // adds some touch tolerance around the
                                    // scrubber

    protected int mScrubberLeft;
    protected int mScrubberTop;
    protected int mScrubberCorrection;
    protected boolean mScrubbing;
    protected boolean mShowTimes;
    protected boolean mShowScrubber;
    private boolean mEnableScrubbing;

    protected int mTotalTime;
    protected int mCurrentTime;

    protected final Rect mTimeBounds;

    protected int mVPaddingInPx;
    private int mLastShowTime = UNKNOWN;

    private ITimeBarSecondaryProgressExt mSecondaryProgressExt = new TimeBarSecondaryProgressExtImpl();
    private ITimeBarInfoExt mInfoExt = new TimeBarInfoExtImpl();
    private ITimeBarLayoutExt mLayoutExt = new TimeBarLayoutExtImpl();

    public TimeBar(Context context, Listener listener) {
        super(context);
        mListener = Utils.checkNotNull(listener);

        mShowTimes = true;
        mShowScrubber = true;

        mProgressBar = new Rect();
        mPlayedBar = new Rect();

        mProgressPaint = new Paint();
        mProgressPaint.setColor(0xFF808080);
        mPlayedPaint = new Paint();
        mPlayedPaint.setColor(0xFFFFFFFF);

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float textSizeInPx = metrics.density * TEXT_SIZE_IN_DP;
        mTimeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTimeTextPaint.setColor(0xFFCECECE);
        mTimeTextPaint.setTextSize(textSizeInPx);
        mTimeTextPaint.setTextAlign(Paint.Align.CENTER);

        mTimeBounds = new Rect();
        mTimeTextPaint.getTextBounds("0:00:00", 0, 7, mTimeBounds);

        mScrubber = BitmapFactory.decodeResource(getResources(), R.drawable.scrubber_knob);
        mScrubberPadding = (int) (metrics.density * SCRUBBER_PADDING_IN_DP);

        mVPaddingInPx = (int) (metrics.density * V_PADDING_IN_DP);
        mLayoutExt.init(mScrubberPadding, mVPaddingInPx);
        mInfoExt.init(textSizeInPx);
        mSecondaryProgressExt.init();
    }

    private void update() {
        mPlayedBar.set(mProgressBar);

        if (mTotalTime > 0) {
            if (View.LAYOUT_DIRECTION_RTL == TextUtils
                    .getLayoutDirectionFromLocale(Locale.getDefault())) {
                // The progress bar should be reversed in RTL.
                mPlayedBar.left = mPlayedBar.right
                        - (int) ((mProgressBar.width() * (long) mCurrentTime) / mTotalTime);
            } else {
                mPlayedBar.right = mPlayedBar.left
                        + (int) ((mProgressBar.width() * (long) mCurrentTime) / mTotalTime);
            }
            /*
             * M: if duration is not accurate, here just adjust playedBar we
             * also show the accurate position text to final user.
             */
            if (mPlayedBar.right > mProgressBar.right) {
                mPlayedBar.right = mProgressBar.right;
            }
        } else {
            if (View.LAYOUT_DIRECTION_RTL == TextUtils
                    .getLayoutDirectionFromLocale(Locale.getDefault())) {
                // The progress bar should be reversed in RTL.
                mPlayedBar.left = mProgressBar.right;
            } else {
                mPlayedBar.right = mProgressBar.left;
            }
        }

        if (!mScrubbing) {
            if (View.LAYOUT_DIRECTION_RTL == TextUtils.getLayoutDirectionFromLocale(
                    Locale.getDefault())) {
                // The progress bar should be reversed in RTL.
                mScrubberLeft = mPlayedBar.left - mScrubber.getWidth() / 2;
            } else {
                mScrubberLeft = mPlayedBar.right - mScrubber.getWidth() / 2;
            }
        }
        // update text bounds when layout changed or time changed
        updateBounds();
        mInfoExt.updateVisibleText(this, mProgressBar, mTimeBounds);
        invalidate();
    }

    /**
     * @return the preferred height of this view, including invisible padding
     */
    public int getPreferredHeight() {
        int preferredHeight = mTimeBounds.height() + mVPaddingInPx + mScrubberPadding;
        return mLayoutExt.getPreferredHeight(preferredHeight, mTimeBounds);
    }

    /**
     * @return the height of the time bar, excluding invisible padding
     */
    public int getBarHeight() {
        int barHeight = mTimeBounds.height() + mVPaddingInPx;
        return mLayoutExt.getBarHeight(barHeight, mTimeBounds);
    }

    public void setTime(int currentTime, int totalTime,
            int trimStartTime, int trimEndTime) {
        if (mCurrentTime == currentTime && mTotalTime == totalTime) {
            return;
        }
        mCurrentTime = currentTime;
        mTotalTime = Math.abs(totalTime);
        if (totalTime <= 0) { /// M: disable scrubbing before mediaplayer ready.
            setScrubbing(false);
        }
        update();
    }

    private boolean inScrubber(float x, float y) {
        int scrubberRight = mScrubberLeft + mScrubber.getWidth();
        int scrubberBottom = mScrubberTop + mScrubber.getHeight();
        return mScrubberLeft - mScrubberPadding < x && x < scrubberRight + mScrubberPadding
                && mScrubberTop - mScrubberPadding < y && y < scrubberBottom + mScrubberPadding;
    }

    private void clampScrubber() {
        int half = mScrubber.getWidth() / 2;
        int max = mProgressBar.right - half;
        int min = mProgressBar.left - half;
        mScrubberLeft = Math.min(max, Math.max(min, mScrubberLeft));
    }

    private int getScrubberTime() {
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            // The progress bar's scrubber time should be reversed in RTL.
            return (int) ((long) (mProgressBar.width() - (mScrubberLeft
                    + mScrubber.getWidth() / 2 - mProgressBar.left))
                    * mTotalTime / mProgressBar.width());
        } else {
            return (int) ((long) (mScrubberLeft + mScrubber.getWidth() / 2 - mProgressBar.left)
                    * mTotalTime / mProgressBar.width());
        }
  }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        if (!mShowTimes && !mShowScrubber) {
            mProgressBar.set(0, 0, w, h);
        } else {
            int margin = mScrubber.getWidth() / 3;
            if (mShowTimes) {
                margin += mTimeBounds.width();
            }
            margin = mLayoutExt.getProgressMargin(margin);
            int progressY = (h + mScrubberPadding) / 2 + mLayoutExt.getProgressOffset(mTimeBounds);
            mScrubberTop = progressY - mScrubber.getHeight() / 2 + 1;
            mProgressBar.set(
                    getPaddingLeft() + margin, progressY,
                    w - getPaddingRight() - margin, progressY + 4);
        }
        update();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // draw progress bars
        canvas.drawRect(mProgressBar, mProgressPaint);
        mSecondaryProgressExt.draw(canvas, mProgressBar);
        canvas.drawRect(mPlayedBar, mPlayedPaint);

        // draw scrubber and timers
        if (mShowScrubber) {
            canvas.drawBitmap(mScrubber, mScrubberLeft, mScrubberTop, null);
        }
        if (mShowTimes) {
            if (View.LAYOUT_DIRECTION_RTL == TextUtils
                    .getLayoutDirectionFromLocale(Locale.getDefault())) {
                // The progress bar's time should be reversed in RTL.
                canvas.drawText(
                        stringForTime(mCurrentTime),
                        getWidth() - getPaddingRight() - mTimeBounds.width() / 2,
                        mTimeBounds.height() + mVPaddingInPx / 2 + mScrubberPadding + 1,
                        mTimeTextPaint);
                canvas.drawText(
                        stringForTime(mTotalTime),
                        mTimeBounds.width() / 2 + getPaddingLeft(),
                        mTimeBounds.height() + mVPaddingInPx / 2 + mScrubberPadding + 1,
                        mTimeTextPaint);
            } else {
                canvas.drawText(
                        stringForTime(mCurrentTime),
                        mTimeBounds.width() / 2 + getPaddingLeft(),
                        mTimeBounds.height() + mVPaddingInPx / 2 + mScrubberPadding + 1,
                        mTimeTextPaint);
                canvas.drawText(
                        stringForTime(mTotalTime),
                        getWidth() - getPaddingRight() - mTimeBounds.width() / 2,
                        mTimeBounds.height() + mVPaddingInPx / 2 + mScrubberPadding + 1,
                        mTimeTextPaint);
            }
        }
        mInfoExt.draw(canvas, mLayoutExt.getInfoBounds(this, mTimeBounds));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (LOG) {
            Log.v(TAG, "onTouchEvent() showScrubber=" + mShowScrubber
                    + ", enableScrubbing=" + mEnableScrubbing + ", totalTime="
                    + mTotalTime + ", scrubbing=" + mScrubbing + ", event="
                    + event);
        }
        if (mShowScrubber && mEnableScrubbing) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    mScrubberCorrection = inScrubber(x, y)
                            ? x - mScrubberLeft
                            : mScrubber.getWidth() / 2;
                    mScrubbing = true;
                    mListener.onScrubbingStart();
                }
                // fall-through
                case MotionEvent.ACTION_MOVE: {
                    mScrubberLeft = x - mScrubberCorrection;
                    clampScrubber();
                    mCurrentTime = getScrubberTime();
                    mListener.onScrubbingMove(mCurrentTime);
                    update();
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (mScrubbing) {
                        mListener.onScrubbingEnd(getScrubberTime(), 0, 0);
                        mScrubbing = false;
                        update();
                        return true;
                    }
                    break;
            }
        }
        return false;
    }

    protected String stringForTime(long millis) {
        int totalSeconds = (int) millis / 1000;
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return String.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    public void setSeekable(boolean canSeek) {
        mShowScrubber = canSeek;
    }

    private void updateBounds() {
        int showTime = mTotalTime > mCurrentTime ? mTotalTime : mCurrentTime;
        if (mLastShowTime == showTime) {
            // do not need to recompute the bounds.
            return;
        }
        String durationText = stringForTime(showTime);
        int length = durationText.length();
        mTimeTextPaint.getTextBounds(durationText, 0, length, mTimeBounds);
        mLastShowTime = showTime;
        if (LOG) {
            Log.v(TAG, "updateBounds() durationText=" + durationText + ", timeBounds="
                    + mTimeBounds);
        }
    }

    public void setScrubbing(boolean enable) {
        if (LOG) {
            Log.v(TAG, "setScrubbing(" + enable + ") scrubbing=" + mScrubbing);
        }
        mEnableScrubbing = enable;
        if (mScrubbing) { // if it is scrubbing, change it to false
            mListener.onScrubbingEnd(getScrubberTime(), 0, 0);
            mScrubbing = false;
        }
    }

    public boolean getScrubbing() {
        if (LOG) {
            Log.v(TAG, "mEnableScrubbing=" + mEnableScrubbing);
        }
        return mEnableScrubbing;
    }

    public void setInfo(String info) {
        if (LOG) {
            Log.v(TAG, "setInfo(" + info + ")");
        }
        mInfoExt.setInfo(info);
        mInfoExt.updateVisibleText(this, mProgressBar, mTimeBounds);
        invalidate();
    }

    public void setSecondaryProgress(int percent) {
        if (LOG) {
            Log.v(TAG, "setSecondaryProgress(" + percent + ")");
        }
        mSecondaryProgressExt.setSecondaryProgress(mProgressBar, percent);
        invalidate();
    }
}

interface ITimeBarInfoExt {
    void init(float textSizeInPx);

    void setInfo(String info);

    void draw(Canvas canvas, Rect infoBounds);

    void updateVisibleText(View parent, Rect progressBar, Rect timeBounds);
}

interface ITimeBarSecondaryProgressExt {
    void init();

    void setSecondaryProgress(Rect progressBar, int percent);

    void draw(Canvas canvas, Rect progressBounds);
}

interface ITimeBarLayoutExt {
    void init(int scrubberPadding, int vPaddingInPx);

    int getPreferredHeight(int originalPreferredHeight, Rect timeBounds);

    int getBarHeight(int originalBarHeight, Rect timeBounds);

    int getProgressMargin(int originalMargin);

    int getProgressOffset(Rect timeBounds);

    int getTimeOffset();

    Rect getInfoBounds(View parent, Rect timeBounds);
}

class TimeBarInfoExtImpl implements ITimeBarInfoExt {
    private static final String TAG = "TimeBarInfoExtensionImpl";
    private static final boolean LOG = false;
    private static final String ELLIPSE = "...";

    private Paint mInfoPaint;
    private Rect mInfoBounds;
    private String mInfoText;
    private String mVisibleText;
    private int mEllipseLength;

    @Override
    public void init(float textSizeInPx) {
        mInfoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mInfoPaint.setColor(0xFFCECECE);
        mInfoPaint.setTextSize(textSizeInPx);
        mInfoPaint.setTextAlign(Paint.Align.CENTER);

        mEllipseLength = (int) Math.ceil(mInfoPaint.measureText(ELLIPSE));
    }

    @Override
    public void draw(Canvas canvas, Rect infoBounds) {
        if (mInfoText != null && mVisibleText != null) {
            canvas.drawText(mVisibleText, infoBounds.centerX(), infoBounds.centerY(), mInfoPaint);
        }
    }

    @Override
    public void setInfo(String info) {
        mInfoText = info;
    }

    public void updateVisibleText(View parent, Rect progressBar, Rect timeBounds) {
        if (mInfoText == null) {
            mVisibleText = null;
            return;
        }
        float tw = mInfoPaint.measureText(mInfoText);
        float space = progressBar.width() - timeBounds.width() * 2 - parent.getPaddingLeft()
                - parent.getPaddingRight();
        if (tw > 0 && space > 0 && tw > space) {
            // we need to cut the info text for visible
            float originalNum = mInfoText.length();
            int realNum = (int) ((space - mEllipseLength) * originalNum / tw);
            if (LOG) {
                Log.v(TAG, "updateVisibleText() infoText=" + mInfoText + " text width=" + tw
                        + ", space=" + space + ", originalNum=" + originalNum + ", realNum="
                        + realNum
                        + ", getPaddingLeft()=" + parent.getPaddingLeft() + ", getPaddingRight()="
                        + parent.getPaddingRight()
                        + ", progressBar=" + progressBar + ", timeBounds=" + timeBounds);
            }
            mVisibleText = mInfoText.substring(0, realNum) + ELLIPSE;
        } else {
            mVisibleText = mInfoText;
        }
        if (LOG) {
            Log.v(TAG, "updateVisibleText() infoText=" + mInfoText + ", visibleText="
                    + mVisibleText
                    + ", text width=" + tw + ", space=" + space);
        }
    }
}

class TimeBarSecondaryProgressExtImpl implements ITimeBarSecondaryProgressExt {
    private static final String TAG = "TimeBarSecondaryProgressExtensionImpl";
    private static final boolean LOG = false;

    private int mBufferPercent;
    private Rect mSecondaryBar;
    private Paint mSecondaryPaint;

    @Override
    public void init() {
        mSecondaryBar = new Rect();
        mSecondaryPaint = new Paint();
        mSecondaryPaint.setColor(0xFF5CA0C5);
    }

    @Override
    public void draw(Canvas canvas, Rect progressBounds) {
        if (mBufferPercent >= 0) {
            mSecondaryBar.set(progressBounds);
            mSecondaryBar.right = mSecondaryBar.left
                    + (int) (mBufferPercent * progressBounds.width() / 100);
            canvas.drawRect(mSecondaryBar, mSecondaryPaint);
        }
        if (LOG) {
            Log.v(TAG, "draw() bufferPercent=" + mBufferPercent + ", secondaryBar="
                    + mSecondaryBar);
        }
    }

    @Override
    public void setSecondaryProgress(Rect progressBar, int percent) {
        mBufferPercent = percent;
    }
}

class TimeBarLayoutExtImpl implements ITimeBarLayoutExt {
    private static final String TAG = "TimeBarLayoutExtensionImpl";
    private static final boolean LOG = false;

    private int mTextPadding;
    private int mVPaddingInPx;

    @Override
    public void init(int scrubberPadding, int vPaddingInPx) {
        mTextPadding = scrubberPadding / 2;
        mVPaddingInPx = vPaddingInPx;
    }

    @Override
    public int getPreferredHeight(int originalPreferredHeight, Rect timeBounds) {
        return originalPreferredHeight + timeBounds.height() + mTextPadding;
    }

    @Override
    public int getBarHeight(int originalBarHeight, Rect timeBounds) {
        return originalBarHeight + timeBounds.height() + mTextPadding;
    }

    @Override
    public int getProgressMargin(int originalMargin) {
        return 0;
    }

    @Override
    public int getProgressOffset(Rect timeBounds) {
        return (timeBounds.height() + mTextPadding) / 2;
    }

    @Override
    public int getTimeOffset() {
        return mTextPadding - mVPaddingInPx / 2;
    }

    @Override
    public Rect getInfoBounds(View parent, Rect timeBounds) {
        Rect bounds = new Rect(parent.getPaddingLeft(), 0,
                parent.getWidth() - parent.getPaddingRight(),
                (timeBounds.height() + mTextPadding * 3 + 1) * 2);
        return bounds;
    }
}
