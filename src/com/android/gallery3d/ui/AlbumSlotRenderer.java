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

import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.app.AlbumDataLoader;
import com.android.gallery3d.app.AlbumPage;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.ColorTexture;
import com.android.gallery3d.glrenderer.FadeInTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.Texture;
import com.android.gallery3d.glrenderer.TiledTexture;
import com.android.gallery3d.glrenderer.UploadedTexture;
import com.android.gallery3d.ui.AlbumSlidingWindow.AlbumEntry;

public class AlbumSlotRenderer extends AbstractSlotRenderer {
    @SuppressWarnings("unused")
    private static final String TAG = "AlbumView";
    private boolean mIsGridViewShown;

    public static class LabelSpec {
        public int labelBackgroundHeight;
        public int titleFontSize;
        public int leftMargin;
        public int iconSize;
        public int titleLeftMargin;
        public int backgroundColor;
        public int titleColor;
        public int borderSize;
    }

    public interface SlotFilter {
        public boolean acceptSlot(int index);
    }

    private final int mPlaceholderColor;
    private static final int CACHE_SIZE = 96;

    private AlbumSlidingWindow mDataWindow;
    private final AbstractGalleryActivity mActivity;
    private final ColorTexture mWaitLoadingTexture;
    private SlotView mSlotView;
    private final SelectionManager mSelectionManager;

    private int mPressedIndex = -1;
    private boolean mAnimatePressedUp;
    private Path mHighlightItemPath = null;
    private boolean mInSelectionMode;

    private SlotFilter mSlotFilter;
    protected final LabelSpec mLabelSpec;

    public AlbumSlotRenderer(AbstractGalleryActivity activity,
            SlotView slotView, LabelSpec labelSpec,
            SelectionManager selectionManager, int placeholderColor,
            boolean viewType) {
        super(activity);
        mActivity = activity;
        mSlotView = slotView;
        mSelectionManager = selectionManager;
        mPlaceholderColor = placeholderColor;
        mLabelSpec = labelSpec;
        mWaitLoadingTexture = new ColorTexture(mPlaceholderColor);
        mWaitLoadingTexture.setSize(1, 1);
        mIsGridViewShown = viewType;
    }

    public void setPressedIndex(int index) {
        if (mPressedIndex == index)
            return;
        mPressedIndex = index;
        mSlotView.invalidate();
    }

    public void setPressedUp() {
        if (mPressedIndex == -1)
            return;
        mAnimatePressedUp = true;
        mSlotView.invalidate();
    }

    public void setHighlightItemPath(Path path) {
        if (mHighlightItemPath == path)
            return;
        mHighlightItemPath = path;
        mSlotView.invalidate();
    }

    public void setModel(AlbumDataLoader model) {
        if (mDataWindow != null) {
            mDataWindow.setListener(null);
            mSlotView.setSlotCount(0);
            mDataWindow = null;
        }
        if (model != null) {
            mDataWindow = new AlbumSlidingWindow(mActivity, model, CACHE_SIZE,
                    mLabelSpec, mIsGridViewShown);
            mDataWindow.setListener(new MyDataModelListener());
            mSlotView.setSlotCount(model.size());

        }
    }

    private static Texture checkTexture(Texture texture) {
        return (texture instanceof TiledTexture)
                && !((TiledTexture) texture).isReady() ? null : texture;
    }

    @Override
    public int renderSlot(GLCanvas canvas, int index, int pass, int width,
            int height) {
        int thumbSize = 0;
        if (!mIsGridViewShown) {
            thumbSize = mLabelSpec.iconSize;
        }
        if (mSlotFilter != null && !mSlotFilter.acceptSlot(index))
            return 0;

        AlbumSlidingWindow.AlbumEntry entry = mDataWindow.get(index);

        int renderRequestFlags = 0;

        Texture content = checkTexture(entry.content);
        if (content == null) {
            content = mWaitLoadingTexture;
            entry.isWaitDisplayed = true;
        } else if (entry.isWaitDisplayed) {
            entry.isWaitDisplayed = false;
            content = new FadeInTexture(mPlaceholderColor, entry.bitmapTexture);
            entry.content = content;
        }
        if (mIsGridViewShown) {
            drawContent(canvas, content, width, height, entry.rotation);
        } else {
            // In List View, the content is always rendered in to the largest square that fits
            // inside the slot, aligned to the top of the slot.
            int minSize = Math.min(width, height);
            drawContent(canvas, content, minSize, minSize, entry.rotation);
        }
        if ((content instanceof FadeInTexture)
                && ((FadeInTexture) content).isAnimating()) {
            renderRequestFlags |= SlotView.RENDER_MORE_FRAME;
        }
        if (!mIsGridViewShown)
            renderRequestFlags |= renderLabel(canvas, entry, width, height);

        if (entry.mediaType == MediaObject.MEDIA_TYPE_VIDEO) {
            drawVideoOverlay(canvas, width, height, mIsGridViewShown, thumbSize);
        }

        if ((entry.mediaType == MediaObject.MEDIA_TYPE_DRM_VIDEO)
                || (entry.mediaType == MediaObject.MEDIA_TYPE_DRM_IMAGE)) {
            drawDrmOverlay(canvas, width, height, entry.mediaType,
                    mIsGridViewShown, thumbSize);
        }

        if (entry.isPanorama) {
            drawPanoramaIcon(canvas, width, height, mIsGridViewShown, thumbSize);
        }

        renderRequestFlags |= renderOverlay(canvas, index, entry, width, height);

        return renderRequestFlags;
    }

    protected int renderLabel(GLCanvas canvas, AlbumEntry entry, int width,
            int height) {
        Texture content = checkLabelTexture(entry.labelTexture);
        if (content == null) {
            content = mWaitLoadingTexture;
        }
        int b = AlbumLabelMaker.getBorderSize();
        int h = mLabelSpec.labelBackgroundHeight;
        content.draw(canvas, -b, height - h + b, width + b + b, h);

        return 0;
    }

    private int renderOverlay(GLCanvas canvas, int index,
            AlbumSlidingWindow.AlbumEntry entry, int width, int height) {
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
        } else if (mInSelectionMode
                && mSelectionManager.isItemSelected(entry.path)) {
            drawSelectedFrame(canvas, width, height);
        }
        return renderRequestFlags;
    }

    private static Texture checkLabelTexture(Texture texture) {
        return ((texture instanceof UploadedTexture) && ((UploadedTexture) texture)
                .isUploading()) ? null : texture;
    }

    private class MyDataModelListener implements AlbumSlidingWindow.Listener {
        @Override
        public void onContentChanged() {
            mSlotView.invalidate();

        }

        @Override
        public void onSizeChanged(int size) {
            mSlotView.setSlotCount(size);
            mSlotView.invalidate();

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
        if (!mIsGridViewShown && mDataWindow != null) {
            mDataWindow.onSlotSizeChanged(width, height);
        }
    }

    public void setSlotFilter(SlotFilter slotFilter) {
        mSlotFilter = slotFilter;
    }
}
