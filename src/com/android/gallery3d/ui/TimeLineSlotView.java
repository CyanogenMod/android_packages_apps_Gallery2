/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 * Not a Contribution
 *
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
import android.text.TextUtils;
import android.util.Log;
import android.os.SystemProperties;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.android.gallery3d.anim.Animation;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.glrenderer.GLCanvas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

public class TimeLineSlotView extends GLView {
    @SuppressWarnings("unused")
    private static final String TAG = "TimeLineSlotView";

    public static final int INDEX_NONE = -1;
    private static final int mainKey = SystemProperties.getInt("qemu.hw.mainkeys", 1);

    public static final int RENDER_MORE_PASS = 1;
    public static final int RENDER_MORE_FRAME = 2;

    private int mWidth  = 0;

    public interface Listener {
        public void onDown(int index);
        public void onUp(boolean followedByLongPress);
        public void onSingleTapUp(int index, boolean isTitle);
        public void onLongTap(int index, boolean isTitle);
        public void onScrollPositionChanged(int position, int total);
    }

    public static class SimpleListener implements Listener {
        @Override public void onDown(int index) {}
        @Override public void onUp(boolean followedByLongPress) {}
        @Override public void onSingleTapUp(int index, boolean isTitle) {}
        @Override public void onLongTap(int index, boolean isTitle) {}
        @Override public void onScrollPositionChanged(int position, int total) {}
    }

    private final GestureDetector mGestureDetector;
    private final ScrollerHelper mScroller;

    private Listener mListener;
    private SlotAnimation mAnimation = null;
    private final Layout mLayout = new Layout();
    private int mStartIndex = INDEX_NONE;

    // whether the down action happened while the view is scrolling.
    private boolean mDownInScrolling;
    private int mOverscrollEffect = OVERSCROLL_3D;

    private TimeLineSlotRenderer mRenderer;

    private int[] mRequestRenderSlots = new int[16];

    public static final int OVERSCROLL_3D = 0;

    // to prevent allocating memory
    private final Rect mTempRect = new Rect();

    // Flag to check whether it is come from Photo Page.
    private boolean isFromPhotoPage = false;

    public TimeLineSlotView(AbstractGalleryActivity activity, Spec spec) {
        mGestureDetector = new GestureDetector(activity, new MyGestureListener());
        mScroller = new ScrollerHelper(activity);
        setSlotSpec(spec);
    }

    public void setSlotRenderer(TimeLineSlotRenderer slotDrawer) {
        mRenderer = slotDrawer;
        if (mRenderer != null) {
            mRenderer.onVisibleRangeChanged(getVisibleStart(), getVisibleEnd());
        }
    }

    public void setCenterIndex(int index) {
        int slotCount = mLayout.mSlotCount;
        if (index < 0 || index >= slotCount) {
            return;
        }
        Rect rect = mLayout.getSlotRect(index, mTempRect);
        int position = (rect.top + rect.bottom - getHeight()) / 2;

        setScrollPosition(position);
    }

    public void makeSlotVisible(int index) {
        Rect rect = mLayout.getSlotRect(index, mTempRect);
        int visibleBegin = mScrollY;
        int visibleLength = getHeight();
        int visibleEnd = visibleBegin + visibleLength;
        int slotBegin = rect.top;
        int slotEnd = rect.bottom;

        int position = visibleBegin;
        if (visibleLength < slotEnd - slotBegin) {
            position = visibleBegin;
        } else if (slotBegin < visibleBegin) {
            position = slotBegin;
        } else if (slotEnd > visibleEnd && mainKey == 1) {
            position = slotEnd - visibleLength;
        } else if (slotBegin > visibleEnd && mainKey == 0) {
            position = slotBegin - visibleLength;
        }

        setScrollPosition(position);
    }

    /**
     * Set the flag which used for check whether it is come from Photo Page.
     */
    public void setIsFromPhotoPage(boolean flag) {
        isFromPhotoPage = flag;
    }


    public void setScrollPosition(int position) {
        /*if (View.LAYOUT_DIRECTION_RTL == TextUtils
            .getLayoutDirectionFromLocale(Locale.getDefault())
            && position == 0 && !isFromPhotoPage) {
        // If RTL and not from Photo Page, set position to max.
        position = mLayout.getScrollLimit();
    }*/

        position = Utils.clamp(position, 0, mLayout.getScrollLimit());
        mScroller.setPosition(position);
        updateScrollPosition(position, false);
    }

    public void setSlotSpec(Spec spec) {
        mLayout.setSlotSpec(spec);
    }

    @Override
    protected void onLayout(boolean changeSize, int l, int t, int r, int b) {
        if (!changeSize) return;
        mWidth = r - l;

        // Make sure we are still at a resonable scroll position after the size
        // is changed (like orientation change). We choose to keep the center
        // visible slot still visible. This is arbitrary but reasonable.
        int visibleIndex =
                (mLayout.getVisibleStart() + mLayout.getVisibleEnd()) / 2;
        mLayout.setSize(r - l, b - t);
        makeSlotVisible(visibleIndex);
        if (mOverscrollEffect == OVERSCROLL_3D) {
        }
    }

    public void startScatteringAnimation(RelativePosition position) {
        mAnimation = new ScatteringAnimation(position);
        mAnimation.start();
        if (mLayout.mSlotCount != 0) invalidate();
    }

    public void startRisingAnimation() {
        mAnimation = new RisingAnimation();
        mAnimation.start();
        if (mLayout.mSlotCount != 0) invalidate();
    }

    private void updateScrollPosition(int position, boolean force) {
        if (!force && (position == mScrollY)) return;
        mScrollY = position;
        mLayout.setScrollPosition(position);
        onScrollPositionChanged(position);
    }

    protected void onScrollPositionChanged(int newPosition) {
        int limit = mLayout.getScrollLimit();
        mListener.onScrollPositionChanged(newPosition, limit);
    }

    public Rect getSlotRect(int slotIndex) {
        return mLayout.getSlotRect(slotIndex, new Rect());
    }

    @Override
    protected boolean onTouch(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownInScrolling = !mScroller.isFinished();
                mScroller.forceFinished();
                break;
            case MotionEvent.ACTION_UP:
                invalidate();
                break;
        }
        return true;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    private static int[] expandIntArray(int array[], int capacity) {
        while (array.length < capacity) {
            array = new int[array.length * 2];
        }
        return array;
    }

    @Override
    protected void render(GLCanvas canvas) {
        super.render(canvas);

        if (mRenderer == null) return;
        mRenderer.prepareDrawing();

        long animTime = AnimationTime.get();
        boolean more = mScroller.advanceAnimation(animTime);
        int oldX = mScrollX;
        updateScrollPosition(mScroller.getPosition(), false);

        if (mOverscrollEffect == OVERSCROLL_3D) {
            // Check if an edge is reached and notify mPaper if so
            int newX = mScrollX;
            int limit = mLayout.getScrollLimit();
            if (oldX > 0 && newX == 0 || oldX < limit && newX == limit) {
                float v = mScroller.getCurrVelocity();
                if (newX == limit) v = -v;

                // I don't know why, but getCurrVelocity() can return NaN.
                if (!Float.isNaN(v)) {
                    //mPaper.edgeReached(v);
                }
                //paperActive = mPaper.advanceAnimation();
            }
        }

        //more |= paperActive;

        if (mAnimation != null) {
            more |= mAnimation.calculate(animTime);
        }

        canvas.translate(-mScrollX, -mScrollY);

        int requestCount = 0;
        int requestedSlot[] = expandIntArray(mRequestRenderSlots,
                mLayout.getVisibleEnd() - mLayout.getVisibleStart());

        for (int i = mLayout.getVisibleEnd() - 1; i >= mLayout.getVisibleStart(); --i) {
            int r = renderItem(canvas, i, 0, false);
            if ((r & RENDER_MORE_FRAME) != 0) more = true;
            if ((r & RENDER_MORE_PASS) != 0) requestedSlot[requestCount++] = i;
        }

        for (int pass = 1; requestCount != 0; ++pass) {
            int newCount = 0;
            for (int i = 0; i < requestCount; ++i) {
                int r = renderItem(canvas,
                        requestedSlot[i], pass, false);
                if ((r & RENDER_MORE_FRAME) != 0) more = false;
                if ((r & RENDER_MORE_PASS) != 0) requestedSlot[newCount++] = i;
            }
            requestCount = newCount;
        }

        canvas.translate(mScrollX, mScrollY);

        if (more) invalidate();

    }

    private int renderItem(
            GLCanvas canvas, int index, int pass, boolean paperActive) {
        Rect rect = mLayout.getSlotRect(index, mTempRect);
        if (rect == null) return 0;
        canvas.save(GLCanvas.SAVE_FLAG_ALPHA | GLCanvas.SAVE_FLAG_MATRIX);
        canvas.translate(rect.left, rect.top, 0);
        if (mAnimation != null && mAnimation.isActive()) {
            mAnimation.apply(canvas, index, rect);
        }
        int result = mRenderer.renderSlot(
                canvas, index, pass, rect.right - rect.left, rect.bottom - rect.top);
        canvas.restore();
        return result;
    }

    public static abstract class SlotAnimation extends Animation {
        protected float mProgress = 0;

        public SlotAnimation() {
            setInterpolator(new DecelerateInterpolator(4));
            setDuration(1500);
        }

        @Override
        protected void onCalculate(float progress) {
            mProgress = progress;
        }

        abstract public void apply(GLCanvas canvas, int slotIndex, Rect target);
    }

    public static class RisingAnimation extends SlotAnimation {
        private static final int RISING_DISTANCE = 128;

        @Override
        public void apply(GLCanvas canvas, int slotIndex, Rect target) {
            canvas.translate(0, 0, RISING_DISTANCE * (1 - mProgress));
        }
    }

    public static class ScatteringAnimation extends SlotAnimation {
        private int PHOTO_DISTANCE = 1000;
        private RelativePosition mCenter;

        public ScatteringAnimation(RelativePosition center) {
            mCenter = center;
        }

        @Override
        public void apply(GLCanvas canvas, int slotIndex, Rect target) {
            canvas.translate(
                    (mCenter.getX() - target.centerX()) * (1 - mProgress),
                    (mCenter.getY() - target.centerY()) * (1 - mProgress),
                    slotIndex * PHOTO_DISTANCE * (1 - mProgress));
            canvas.setAlpha(mProgress);
        }
    }

    private class MyGestureListener implements GestureDetector.OnGestureListener {
        private boolean isDown;

        // We call the listener's onDown() when our onShowPress() is called and
        // call the listener's onUp() when we receive any further event.
        @Override
        public void onShowPress(MotionEvent e) {
            GLRoot root = getGLRoot();
            root.lockRenderThread();
            try {
                if (isDown) return;
                int index = mLayout.getSlotIndexByPosition(e.getX(), e.getY());
                if (index != INDEX_NONE) {
                    isDown = true;
                    mListener.onDown(index);
                }
            } finally {
                root.unlockRenderThread();
            }
        }

        private void cancelDown(boolean byLongPress) {
            if (!isDown) return;
            isDown = false;
            mListener.onUp(byLongPress);
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return false;
        }

        @Override
        public boolean onFling(MotionEvent e1,
                MotionEvent e2, float velocityX, float velocityY) {
            cancelDown(false);
            int scrollLimit = mLayout.getScrollLimit();
            if (scrollLimit == 0) return false;
            mScroller.fling((int) -velocityY, 0, scrollLimit);
            invalidate();
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1,
                MotionEvent e2, float distanceX, float distanceY) {
            cancelDown(false);
            int overDistance = mScroller.startScroll(
                    Math.round(distanceY), 0, mLayout.getScrollLimit());
            if (mOverscrollEffect == OVERSCROLL_3D && overDistance != 0) {
                //mPaper.overScroll(overDistance);
            }
            invalidate();
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            cancelDown(false);
            if (mDownInScrolling) return true;
            RectSlot slot = mLayout.getRectSlotByPosition(e.getX(), e.getY());
                if (slot == null) {
                    return true;
                }
                int index = slot.slotIndex;
                if (index != INDEX_NONE) {
                    if (slot.mediaType == MediaObject.MEDIA_TYPE_TIMELINE_TITLE) {
                        mListener.onSingleTapUp(index, true);
                        return true;
                    }
                    mListener.onSingleTapUp(index, false);
                }
                return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            cancelDown(true);
            if (mDownInScrolling) return;
            lockRendering();
            try {
                RectSlot slot = mLayout.getRectSlotByPosition(e.getX(), e.getY());
                if (slot == null) return;

                int index = slot.slotIndex;
                if (index != INDEX_NONE) {
                    if (slot.mediaType == MediaObject.MEDIA_TYPE_TIMELINE_TITLE) {
                       mListener.onLongTap(index, true);

                    } else {
                    mListener.onLongTap(index, false);
                    }
                }
            } finally {
                unlockRendering();
            }
        }
    }

    public void setStartIndex(int index) {
        mStartIndex = index;
    }

    // Return true if the layout parameters have been changed
    public boolean setSlotCount(int slotCount) {
        boolean changed = mLayout.setSlotCount(slotCount);

        // mStartIndex is applied the first time setSlotCount is called.
        if (mStartIndex != INDEX_NONE) {
            setCenterIndex(mStartIndex);
            mStartIndex = INDEX_NONE;
        }
        // Reset the scroll position to avoid scrolling over the updated limit.
        setScrollPosition(mScrollY);
        return changed;
    }

    public void onVersionChanged() {
        mLayout.createSlots();
        invalidate();
    }

    public int getVisibleStart() {
        return mLayout.getVisibleStart();
    }

    public int getVisibleEnd() {
        return mLayout.getVisibleEnd();
    }

    public int getScrollX() {
        return mScrollX;
    }

    public int getScrollY() {
        return mScrollY;
    }

    public Rect getSlotRect(int slotIndex, GLView rootPane) {
        // Get slot rectangle relative to this root pane.
        Rect offset = new Rect();
        rootPane.getBoundsOf(this, offset);
        Rect r = getSlotRect(slotIndex);
        r.offset(offset.left - getScrollX(),
                offset.top - getScrollY());
        return r;
    }

    public int getTitleWidth() {
        return mWidth;
    }

    // This Spec class is used to specify the size of each slot in the SlotView.
    // There are two ways to do it:
    //
    // Specify colsLand, colsPort, and slotGap: they specify the number
    // of rows in landscape/portrait mode and the gap between slots. The
    // width and height of each slot is determined automatically.
    //
    // The initial value of -1 means they are not specified.
    public static class Spec {
        public int colsLand = -1;
        public int colsPort = -1;
        public int titleHeight = -1;
        public int slotGapPort = -1;
        public int slotGapLand = -1;
    }

    public class Layout {

        private int mVisibleStart;
        private int mVisibleEnd;

        public int mSlotCount;
        private int mSlotWidth;
        private int mSlotHeight;
        private int mSlotGap;

        private Spec mSpec;

        private int mWidth;
        private int mHeight;

        private int mUnitCount;
        private int mContentLength;
        private int mScrollPosition;

        private ArrayList<Integer> mHeightList;
        private HashMap<Integer, RectSlot> mMediaSlotMap;

        public void setSlotSpec(TimeLineSlotView.Spec spec) {
            mSpec = spec;
        }

        public boolean setSlotCount(int slotCount) {
            if (slotCount == mSlotCount) return false;
            mSlotCount = slotCount;
            initLayoutParameters();
            updateVisibleSlotRange();
            return true;
        }

        public Rect getSlotRect(int index, Rect rect) {
            if (index >= mVisibleStart && index < mVisibleEnd && mVisibleEnd != 0) {
                RectSlot slot = getRectSlot(index);
                if (slot == null) {
                    return null;
                }
                return getSlotRect(slot, rect);
            }
            return rect;
        }

        private void initLayoutParameters() {
            mUnitCount = (mWidth > mHeight) ? mSpec.colsLand : mSpec.colsPort;
            mSlotGap = (mWidth > mHeight) ? mSpec.slotGapLand: mSpec.slotGapPort;
            mSlotWidth = Math.round((mWidth - (mUnitCount - 1) * mSlotGap) / mUnitCount);
            mSlotHeight = mSlotWidth;
            if (mRenderer != null) {
                mRenderer.onSlotSizeChanged(mSlotWidth, mSlotHeight);
            }
        }

        private void setSize(int width, int height) {
            mWidth = width;
            mHeight = height;
            initLayoutParameters();
            createSlots();
            updateVisibleSlotRange();
        }

        public void setScrollPosition(int position) {
            if (mScrollPosition == position) return;
            mScrollPosition = position;
            updateVisibleSlotRange();
        }

        private Rect getSlotRect(RectSlot slot, Rect rect) {
            if (rect == null) {
                Log.e(TAG, "LAYOUT: null rect passed");
                Utils.assertTrue(false);
            }
            int x = 0;
            int y = 0;
            int w = mSlotWidth;
            int h = 0;
            if (slot.mediaType == MediaObject.MEDIA_TYPE_TIMELINE_TITLE) {
                 x = 0;
                 y = slot.totalHeight - (mSpec.titleHeight);
                 w = mWidth;
                 h = mSpec.titleHeight;
            } else {
                 x = slot.slotCol * (mSlotWidth + mSlotGap);
                 y = slot.totalHeight -(mSlotHeight);
                 w = mSlotWidth;
                 h = mSlotHeight;
            }
            rect.set(x, y, x + w, y + h);
            return rect;
        }

        private synchronized void updateVisibleSlotRange() {
            int position = mScrollPosition;
            if(mHeightList!= null && mHeightList.size()>0 ) {
                int indexStart = Arrays.binarySearch(mHeightList.toArray(new Integer[0]), position);
                int indexEnd = Arrays.binarySearch(mHeightList.toArray(new Integer[0]), position+mHeight);
                if(indexStart<0) {
                    indexStart = (indexStart * (- 1)) - 1;
                } if (indexEnd<0) {
                    indexEnd = (indexEnd * (- 1)) - 1;
                }

                if (indexStart > mHeightList.size() - 1) {
                    indexStart = mHeightList.size() - 1;
                }
                int startSlotIndex = mHeightList.indexOf(mHeightList.get(indexStart));
                if(indexEnd> mHeightList.size()-1) {
                    indexEnd = mHeightList.size()-1;
                }
                int endSlotIndex = mHeightList.lastIndexOf(mHeightList.get(indexEnd));


                int endSlotIndexStr = mHeightList.indexOf(mHeightList.get(indexEnd));
                if(((endSlotIndex-endSlotIndexStr) % mUnitCount) == (mUnitCount-1) && (startSlotIndex>mUnitCount)) {
                    startSlotIndex= startSlotIndex-mUnitCount;
                }

            setVisibleRange(startSlotIndex, Math.min(mSlotCount, endSlotIndex + 1));
            }
        }

        private void setVisibleRange(int start, int end) {
            if (start == mVisibleStart && end == mVisibleEnd) return;
            if (start < end) {
                mVisibleStart = start;
                mVisibleEnd = end;
            } else {
                mVisibleStart = mVisibleEnd = 0;
            }
            if (mRenderer != null) {
                mRenderer.onVisibleRangeChanged(mVisibleStart, mVisibleEnd);
            }
        }

        public int getVisibleStart() {
            return mVisibleStart;
        }

        public int getVisibleEnd() {
            return mVisibleEnd;
        }

        public int getSlotIndexByPosition(float x, float y) {
            RectSlot slot = getRectSlotByPosition(x, y);
            if (slot == null) return INDEX_NONE;

            return slot.slotIndex;

        }

        public RectSlot getRectSlotByPosition(float x, float y) {
            int absoluteX = Math.round(x);
            int absoluteY = Math.round(y) + mScrollPosition;

            if (absoluteX < 0 || absoluteY < 0) {
                return null;
            }
            if (absoluteY > mContentLength)
                return null;

            int columnIdx = absoluteX / (mSlotWidth + mSlotGap);

            RectSlot rectSlot;

            if (mHeightList != null && mHeightList.size() > 0) {
                int index = Arrays.binarySearch(mHeightList.toArray(new Integer[0]), absoluteY);
                if (index < 0) {
                    index = (index * -1) - 1;
                }
                if (index >= mHeightList.size()) {
                    index = mHeightList.size() - 1;
                }
                int startIndex = mHeightList.indexOf(mHeightList.get(index));
                int maxIndex = mHeightList.lastIndexOf(mHeightList.get(index));
                if (mMediaSlotMap.get(startIndex).mediaType == 
                        MediaObject.MEDIA_TYPE_TIMELINE_TITLE) {
                    return mMediaSlotMap.get(startIndex);
                }
                for (int i = startIndex; (i < startIndex + mUnitCount) && (i <= maxIndex); i++) {
                    rectSlot = mMediaSlotMap.get(i);
                    if (rectSlot.slotCol == columnIdx) {
                        return rectSlot;
                    }
                }
            }
            return null;
        }

        public int getScrollLimit() {
            if (mHeightList != null && mHeightList.size() > 0) {
                return Math.max(0, mContentLength -mHeight);
            }
            return 0;
        }



        public void createSlots() {
            ArrayList<MediaItem> mediaItemlist = new ArrayList<MediaItem>();
            if (mRenderer != null) {
                mediaItemlist = mRenderer.getAllMediaItems();
            }
            if (mHeightList == null) {
                mHeightList = new ArrayList<Integer>();
            }
            if (mMediaSlotMap == null) {
                mMediaSlotMap = new HashMap<Integer, RectSlot>();
            }
            mHeightList.clear();
            mMediaSlotMap.clear();
            if (mediaItemlist != null && mediaItemlist.size() > 0) {
                boolean isPrevTitle = false;
                int j = 0;
                int col = 0;
                int totalHeight = 0;
                for (int i = 0; i < mediaItemlist.size(); ++i) {
                    MediaItem info = mediaItemlist.get(i);

                    if (info.getMediaType() == MediaObject.MEDIA_TYPE_TIMELINE_TITLE) {
                        totalHeight += (mSpec.titleHeight + mSlotGap);
                        isPrevTitle = true;
                        col = 0;
                    } else {
                        if (isPrevTitle) {
                            j = 0;
                            isPrevTitle = false;
                        } else {
                            ++j;
                        }
                        if (j % mUnitCount == 0) {
                            totalHeight += (mSlotHeight + mSlotGap);
                            col = 0;
                        } else {
                            col = j % mUnitCount;
                        }

                    }
                    mHeightList.add(totalHeight);
                    if (View.LAYOUT_DIRECTION_RTL == TextUtils
                            .getLayoutDirectionFromLocale(Locale.getDefault())) {
                        col = mUnitCount - col - 1;
                    }
                    RectSlot rectslot = new RectSlot(info.getMediaType(), i, col, totalHeight);
                    mMediaSlotMap.put(rectslot.slotIndex, rectslot);
                }
                mContentLength = mHeightList.get(mHeightList.size() -1);
                mSlotCount = mediaItemlist.size();
            }
            updateVisibleSlotRange();
        }

        public RectSlot getRectSlot(int slotIndex) {
            return mMediaSlotMap.get(slotIndex);
        }
    }



    public static class RectSlot {
        public int mediaType;
        public int slotIndex;
        public int slotCol;
        public int totalHeight;

        public RectSlot(int type, int index, int col, int height) {
            mediaType = type;
            slotIndex = index;
            slotCol = col;
            totalHeight = height;
        }
    }
}
