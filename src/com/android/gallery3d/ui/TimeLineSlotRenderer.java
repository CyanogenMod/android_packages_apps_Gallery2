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


import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.app.TimeLineDataLoader;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.ColorTexture;
import com.android.gallery3d.glrenderer.FadeInTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.Texture;
import com.android.gallery3d.glrenderer.TiledTexture;

import java.util.ArrayList;

public class TimeLineSlotRenderer extends AbstractSlotRenderer {

    @SuppressWarnings("unused")
    private static final String TAG = "AlbumView";

    private final int mPlaceholderColor;
    private static final int CACHE_SIZE = 96;

    private TimeLineSlidingWindow mDataWindow;
    private final AbstractGalleryActivity mActivity;
    private final ColorTexture mWaitLoadingTexture;
    private final TimeLineSlotView mSlotView;
    private final SelectionManager mSelectionManager;

    private int mPressedIndex = -1;
    private boolean mAnimatePressedUp;
    private Path mHighlightItemPath = null;
    private boolean mInSelectionMode;

    private AlbumSlotRenderer.SlotFilter mSlotFilter;
    private final LabelSpec mLabelSpec;

    public static class LabelSpec {

        public int timeLineTitleHeight;
        public int timeLineTitleFontSize;
        public int timeLineTitleTextColor;
        public int timeLineNumberTextColor;
        public int timeLineTitleBackgroundColor;
}
    public TimeLineSlotRenderer(AbstractGalleryActivity activity, TimeLineSlotView slotView,
                                    SelectionManager selectionManager, LabelSpec labelSpec,
                                    int placeholderColor) {
        super(activity);
        mActivity = activity;
        mSlotView = slotView;
        mLabelSpec = labelSpec;
        mSelectionManager = selectionManager;
        mPlaceholderColor = placeholderColor;
        mWaitLoadingTexture = new ColorTexture(mPlaceholderColor);
        mWaitLoadingTexture.setSize(1, 1);

    }

    public void setPressedIndex(int index) {
        if (mPressedIndex == index) return;
        mPressedIndex = index;
        mSlotView.invalidate();
    }

    public void setPressedUp() {
        if (mPressedIndex == -1) return;
        mAnimatePressedUp = true;
        mSlotView.invalidate();
    }

    public void setHighlightItemPath(Path path) {
        if (mHighlightItemPath == path) return;
        mHighlightItemPath = path;
        mSlotView.invalidate();
    }

    protected static Texture checkContentTexture(Texture texture) {
        return (texture instanceof TiledTexture)
                && !((TiledTexture) texture).isReady()
                ? null
                : texture;
    }

    protected int renderOverlay(GLCanvas canvas, int index,
            TimeLineSlidingWindow.AlbumEntry entry, int width, int height) {
        int renderRequestFlags = 0;
        if (mPressedIndex == index) {
            if (mAnimatePressedUp) {
                drawPressedUpFrame(canvas, width, height);
                renderRequestFlags |= SlotView.RENDER_MORE_FRAME;
                if (isPressedUpFrameFinished()) {
                    mAnimatePressedUp = false;
                    mPressedIndex = -1;
                }
            } else {
                drawPressedFrame(canvas, width, height);
            }
        } else if ((entry.path != null) && (mHighlightItemPath == entry.path)) {
            drawSelectedFrame(canvas, width, height);
        } else if (mInSelectionMode && entry.mediaType != MediaItem.MEDIA_TYPE_TIMELINE_TITLE
                && mSelectionManager.isItemSelected(entry.path)) {
            drawSelectedFrame(canvas, width, height);
        }
        return renderRequestFlags;
    }

    protected class MyDataModelListener implements TimeLineSlidingWindow.Listener {
        @Override
        public void onContentChanged() {
            mSlotView.invalidate();
        }

        @Override
        public void onSizeChanged(int[] size) {
            mSlotView.setSlotCount(size);
            mSlotView.invalidate();
        }

        @Override
        public int getTitleWidth() {
            return mSlotView.getTitleWidth();
        }

    }

    public void resume() {
        mDataWindow.resume();
    }

    public void pause() {
        mDataWindow.pause();
    }

    @Override
    public void prepareDrawing() {
        mInSelectionMode = mSelectionManager.inSelectionMode();
    }

    @Override
    public void onVisibleRangeChanged(int visibleStart, int visibleEnd) {
        if (mDataWindow != null) {
            mDataWindow.setActiveWindow(visibleStart, visibleEnd);
        }
    }

    @Override
    public void onSlotSizeChanged(int width, int height) {
        if (mDataWindow != null) {
            mDataWindow.onSlotSizeChanged(width, height);
        }
    }

    public void setSlotFilter(AlbumSlotRenderer.SlotFilter slotFilter) {
        mSlotFilter = slotFilter;
    }

    public void setModel(TimeLineDataLoader model) {
        if (mDataWindow != null) {
            mDataWindow.setListener(null);
            mDataWindow = null;
        }
        if (model != null) {
            mDataWindow = new TimeLineSlidingWindow(mActivity, model, CACHE_SIZE, mLabelSpec,
                    mSelectionManager, mSlotView);
            mDataWindow.setListener(new MyDataModelListener());
        }
    }

    @Override
    public int renderSlot(GLCanvas canvas, int index, int pass, int width, int height) {
        if (mSlotFilter != null && !mSlotFilter.acceptSlot(index)) return 0;

        TimeLineSlidingWindow.AlbumEntry entry = mDataWindow.get(index);
        int renderRequestFlags = 0;
        if (entry != null) {
            Texture content = checkContentTexture(entry.content);
            if (content == null) {
                content = mWaitLoadingTexture;
                entry.isWaitDisplayed = true;
            } else if (entry.isWaitDisplayed) {
                entry.isWaitDisplayed = false;
                content = new FadeInTexture(mPlaceholderColor, entry.bitmapTexture);
                entry.content = content;
            }
            drawContent(canvas, content, width, height, entry.rotation);
            if ((content instanceof FadeInTexture) &&
                    ((FadeInTexture) content).isAnimating()) {
                renderRequestFlags |= SlotView.RENDER_MORE_FRAME;
            }

            if (entry.mediaType == MediaObject.MEDIA_TYPE_VIDEO) {
                drawVideoOverlay(canvas, width, height, true, 0);
            }

            renderRequestFlags |= renderOverlay(canvas, index, entry, width, height);
        }
        return renderRequestFlags;
    }
}
