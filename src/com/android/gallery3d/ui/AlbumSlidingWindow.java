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

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.os.Message;
import android.text.TextPaint;
import android.text.TextUtils;

import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.app.AlbumDataLoader;
import com.android.gallery3d.app.AlbumPage;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaObject.PanoramaSupportCallback;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.BitmapTexture;
import com.android.gallery3d.glrenderer.Texture;
import com.android.gallery3d.glrenderer.TextureUploader;
import com.android.gallery3d.glrenderer.TiledTexture;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.JobLimiter;

public class AlbumSlidingWindow implements AlbumDataLoader.DataListener {
    @SuppressWarnings("unused")
    private static final String TAG = "AlbumSlidingWindow";

    private static final int MSG_UPDATE_ENTRY = 0;
    private static final int JOB_LIMIT = 2;
    private static final int MSG_UPDATE_ALBUM_ENTRY = 1;
    public static final String KEY_ALBUM = "Album";

    public static interface Listener {
        public void onSizeChanged(int size);

        public void onContentChanged();
    }

    public static class AlbumEntry {
        public MediaItem item;
        public String name; // For title of image
        public Path path;
        public boolean isPanorama;
        public int rotation;
        public int mediaType;
        public boolean isWaitDisplayed;
        public TiledTexture bitmapTexture;
        public Texture content;
        private BitmapLoader contentLoader;
        private PanoSupportListener mPanoSupportListener;
        public BitmapTexture labelTexture;
        private BitmapLoader labelLoader;

    }

    private final AlbumDataLoader mSource;
    private final AlbumEntry mData[];
    private final SynchronizedHandler mHandler;
    private final JobLimiter mThreadPool;
    private final TiledTexture.Uploader mTileUploader;

    private int mSize;

    private int mContentStart = 0;
    private int mContentEnd = 0;

    private int mActiveStart = 0;
    private int mActiveEnd = 0;

    private Listener mListener;

    private int mActiveRequestCount = 0;
    private boolean mIsActive = false;
    private AlbumLabelMaker mLabelMaker;
    private int mSlotWidth;
    private BitmapTexture mLoadingLabel;
    private TextureUploader mLabelUploader;
    private int mCurrentView;
    private AbstractGalleryActivity mActivity;
    private boolean isSlotSizeChanged;
    private boolean mViewType;

    private class PanoSupportListener implements PanoramaSupportCallback {
        public final AlbumEntry mEntry;

        public PanoSupportListener(AlbumEntry entry) {
            mEntry = entry;
        }

        @Override
        public void panoramaInfoAvailable(MediaObject mediaObject,
                boolean isPanorama, boolean isPanorama360) {
            if (mEntry != null)
                mEntry.isPanorama = isPanorama;
        }
    }

    public AlbumSlidingWindow(AbstractGalleryActivity activity,
            AlbumDataLoader source, int cacheSize,
            AlbumSlotRenderer.LabelSpec labelSpec, boolean viewType) {
        source.setDataListener(this);
        mSource = source;
        mViewType = viewType;
        mData = new AlbumEntry[cacheSize];
        mSize = source.size();
        mHandler = new SynchronizedHandler(activity.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                case 0:
                    Utils.assertTrue(message.what == MSG_UPDATE_ENTRY);
                    ((ThumbnailLoader) message.obj).updateEntry();
                    break;
                case 1:
                    Utils.assertTrue(message.what == MSG_UPDATE_ALBUM_ENTRY);
                    ((EntryUpdater) message.obj).updateEntry();
                    break;
                }

            }
        };

        mThreadPool = new JobLimiter(activity.getThreadPool(), JOB_LIMIT);
        if (!mViewType) {
            mLabelMaker = new AlbumLabelMaker(activity.getAndroidContext(),
                    labelSpec);
            mLabelUploader = new TextureUploader(activity.getGLRoot());
        }
        mTileUploader = new TiledTexture.Uploader(activity.getGLRoot());
        mActivity = activity;

    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public AlbumEntry get(int slotIndex) {
        if (!isActiveSlot(slotIndex)) {
            Utils.fail("invalid slot: %s outsides (%s, %s)", slotIndex,
                    mActiveStart, mActiveEnd);
        }
        return mData[slotIndex % mData.length];
    }

    public boolean isActiveSlot(int slotIndex) {
        return slotIndex >= mActiveStart && slotIndex < mActiveEnd;
    }

    private void setContentWindow(int contentStart, int contentEnd) {
        if (contentStart == mContentStart && contentEnd == mContentEnd)
            return;

        if (!mIsActive) {
            mContentStart = contentStart;
            mContentEnd = contentEnd;
            mSource.setActiveWindow(contentStart, contentEnd);
            return;
        }

        if (contentStart >= mContentEnd || mContentStart >= contentEnd) {
            for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
                freeSlotContent(i);
            }
            mSource.setActiveWindow(contentStart, contentEnd);
            for (int i = contentStart; i < contentEnd; ++i) {
                prepareSlotContent(i);
            }
        } else {
            for (int i = mContentStart; i < contentStart; ++i) {
                freeSlotContent(i);
            }
            for (int i = contentEnd, n = mContentEnd; i < n; ++i) {
                freeSlotContent(i);
            }
            mSource.setActiveWindow(contentStart, contentEnd);
            for (int i = contentStart, n = mContentStart; i < n; ++i) {
                prepareSlotContent(i);
            }
            for (int i = mContentEnd; i < contentEnd; ++i) {
                prepareSlotContent(i);
            }
        }

        mContentStart = contentStart;
        mContentEnd = contentEnd;
    }

    public void setActiveWindow(int start, int end) {
        if (!(start <= end && end - start <= mData.length && end <= mSize)) {
            Utils.fail("%s, %s, %s, %s", start, end, mData.length, mSize);
        }
        AlbumEntry data[] = mData;

        mActiveStart = start;
        mActiveEnd = end;

        int contentStart = Utils.clamp((start + end) / 2 - data.length / 2, 0,
                Math.max(0, mSize - data.length));
        int contentEnd = Math.min(contentStart + data.length, mSize);
        setContentWindow(contentStart, contentEnd);
        updateTextureUploadQueue();
        if (mIsActive)
            updateAllImageRequests();
    }

    private void uploadBgTextureInSlot(int index) {
        if (index < mContentEnd && index >= mContentStart) {
            AlbumEntry entry = mData[index % mData.length];
            if (entry.bitmapTexture != null) {
                mTileUploader.addTexture(entry.bitmapTexture);
            }
            if (!mViewType) {
                if (entry.labelTexture != null) {
                    mLabelUploader.addBgTexture(entry.labelTexture);
                }
            }
        }
    }

    private void updateTextureUploadQueue() {
        if (!mIsActive)
            return;
        mTileUploader.clear();
        if (!mViewType) {
            mLabelUploader.clear();
        }

        // add foreground textures
        for (int i = mActiveStart, n = mActiveEnd; i < n; ++i) {
            AlbumEntry entry = mData[i % mData.length];
            if (entry.bitmapTexture != null) {
                mTileUploader.addTexture(entry.bitmapTexture);
            }
            if (!mViewType) {
                if (entry.labelTexture != null) {
                    mLabelUploader.addFgTexture(entry.labelTexture);
                }
            }
        }

        // add background textures
        int range = Math.max((mContentEnd - mActiveEnd),
                (mActiveStart - mContentStart));
        for (int i = 0; i < range; ++i) {
            uploadBgTextureInSlot(mActiveEnd + i);
            uploadBgTextureInSlot(mActiveStart - i - 1);
        }
    }

    // We would like to request non active slots in the following order:
    // Order: 8 6 4 2 1 3 5 7
    // |---------|---------------|---------|
    // |<- active ->|
    // |<-------- cached range ----------->|
    private void requestNonactiveImages() {
        int range = Math.max((mContentEnd - mActiveEnd),
                (mActiveStart - mContentStart));
        for (int i = 0; i < range; ++i) {
            // requestSlotImage(mActiveEnd + i);
            // requestSlotImage(mActiveStart - 1 - i);
            requestImagesInSlot(mActiveEnd + i);
            requestImagesInSlot(mActiveStart - 1 - i);
        }
        isSlotSizeChanged = false;
    }

    private void requestImagesInSlot(int slotIndex) {
        if (slotIndex < mContentStart || slotIndex >= mContentEnd)
            return;
        AlbumEntry entry = mData[slotIndex % mData.length];
        if (isSlotSizeChanged && !mViewType) {
            if ((entry.content != null || entry.item == null)
                    && entry.labelTexture != null) {
                return;

            } else {
                if (entry.labelLoader != null)
                    entry.labelLoader.startLoad();
            }
        } else {
            if (entry.content != null || entry.item == null)
                return;
            entry.mPanoSupportListener = new PanoSupportListener(entry);
            entry.item.getPanoramaSupport(entry.mPanoSupportListener);
            if (entry.contentLoader != null)
                entry.contentLoader.startLoad();
            if (!mViewType) {

                if (entry.labelLoader != null)
                    entry.labelLoader.startLoad();
            }
        }

    }

    // return whether the request is in progress or not
    private boolean requestSlotImage(int slotIndex) {
        if (slotIndex < mContentStart || slotIndex >= mContentEnd)
            return false;
        AlbumEntry entry = mData[slotIndex % mData.length];
        if (entry.content != null || entry.item == null)
            return false;

        // Set up the panorama callback
        entry.mPanoSupportListener = new PanoSupportListener(entry);
        entry.item.getPanoramaSupport(entry.mPanoSupportListener);

        return entry.contentLoader.isRequestInProgress();
    }

    private static boolean startLoadBitmap(BitmapLoader loader) {
        if (loader == null)
            return false;
        loader.startLoad();
        return loader.isRequestInProgress();
    }

    private void cancelNonactiveImages() {
        int range = Math.max((mContentEnd - mActiveEnd),
                (mActiveStart - mContentStart));
        for (int i = 0; i < range; ++i) {
            cancelSlotImage(mActiveEnd + i);
            cancelSlotImage(mActiveStart - 1 - i);
        }
    }

    private void cancelSlotImage(int slotIndex) {
        if (slotIndex < mContentStart || slotIndex >= mContentEnd)
            return;
        AlbumEntry item = mData[slotIndex % mData.length];
        if (item.contentLoader != null)
            item.contentLoader.cancelLoad();
        if (!mViewType) {
            if (item.labelLoader != null)
                item.labelLoader.cancelLoad();
        }
    }

    private void freeSlotContent(int slotIndex) {
        AlbumEntry data[] = mData;
        int index = slotIndex % data.length;
        AlbumEntry entry = data[index];
        if (entry.contentLoader != null)
            entry.contentLoader.recycle();
        if (!mViewType) {
            if (entry.labelLoader != null)
                entry.labelLoader.recycle();
            if (entry.labelTexture != null)
                entry.labelTexture.recycle();
        }
        if (entry.bitmapTexture != null)
            entry.bitmapTexture.recycle();
        data[index] = null;
    }

    private void prepareSlotContent(int slotIndex) {
        AlbumEntry entry = new AlbumEntry();
        MediaItem item = mSource.get(slotIndex); // item could be null;
        entry.item = item;
        entry.name = (item == null) ? null : item.getName();
        entry.mediaType = (item == null) ? MediaItem.MEDIA_TYPE_UNKNOWN
                : entry.item.getMediaType();
        entry.path = (item == null) ? null : item.getPath();
        entry.rotation = (item == null) ? 0 : item.getRotation();
        entry.contentLoader = new ThumbnailLoader(slotIndex, entry.item);
        if (!mViewType) {
            if (entry.labelLoader != null) {
                entry.labelLoader.recycle();
                entry.labelLoader = null;
                entry.labelTexture = null;
            }
            if (entry.name != null) {
                entry.labelLoader = new AlbumLabelLoader(slotIndex, entry.name);
            }
        }
        mData[slotIndex % mData.length] = entry;
    }

    private void updateAllImageRequests() {
        mActiveRequestCount = 0;
        for (int i = mActiveStart, n = mActiveEnd; i < n; ++i) {
            AlbumEntry entry = mData[i % mData.length];
            if (isSlotSizeChanged) {
                if ((entry.content != null || entry.item == null)
                        && entry.labelTexture != null) {
                    continue;
                } else {
                    if (startLoadBitmap(entry.labelLoader))
                        ++mActiveRequestCount;
                }

            }

            else {
                if (entry.content != null || entry.item == null)
                    continue;
                if (startLoadBitmap(entry.contentLoader))
                    ++mActiveRequestCount;
                if (!mViewType) {
                    if (startLoadBitmap(entry.labelLoader))
                        ++mActiveRequestCount;
                }
            }

            // if (requestSlotImage(i)) ++mActiveRequestCount;
        }

        if (isSlotSizeChanged || mActiveRequestCount == 0) {
            requestNonactiveImages();
        } else {

            cancelNonactiveImages();
        }
    }

    private class ThumbnailLoader extends BitmapLoader {
        private final int mSlotIndex;
        private final MediaItem mItem;

        public ThumbnailLoader(int slotIndex, MediaItem item) {
            mSlotIndex = slotIndex;
            mItem = item;
        }

        @Override
        protected Future<Bitmap> submitBitmapTask(FutureListener<Bitmap> l) {
            return mThreadPool.submit(
                    mItem.requestImage(MediaItem.TYPE_MICROTHUMBNAIL), this);
        }

        @Override
        protected void onLoadComplete(Bitmap bitmap) {
            mHandler.obtainMessage(MSG_UPDATE_ENTRY, this).sendToTarget();
        }

        public void updateEntry() {
            Bitmap bitmap = getBitmap();

            if (bitmap == null)
                return; // error or recycled
            AlbumEntry entry = mData[mSlotIndex % mData.length];
            entry.bitmapTexture = new TiledTexture(bitmap);
            entry.content = entry.bitmapTexture;

            if (isActiveSlot(mSlotIndex)) {
                mTileUploader.addTexture(entry.bitmapTexture);
                --mActiveRequestCount;
                if (mActiveRequestCount == 0)
                    requestNonactiveImages();
                if (mListener != null)
                    mListener.onContentChanged();
            } else {
                mTileUploader.addTexture(entry.bitmapTexture);
            }
        }
    }

    @Override
    public void onSizeChanged(int size) {
        if (mSize != size) {
            mSize = size;
            if (mListener != null)
                mListener.onSizeChanged(mSize);
            if (mContentEnd > mSize)
                mContentEnd = mSize;
            if (mActiveEnd > mSize)
                mActiveEnd = mSize;
        }
    }

    @Override
    public void onContentChanged(int index) {
        if (index >= mContentStart && index < mContentEnd && mIsActive) {
            freeSlotContent(index);
            prepareSlotContent(index);
            updateAllImageRequests();
            if (mListener != null && isActiveSlot(index)) {
                mListener.onContentChanged();
            }
        }
    }

    public void resume() {
        mIsActive = true;
        TiledTexture.prepareResources();
        for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
            prepareSlotContent(i);
        }
        updateAllImageRequests();
    }

    public void pause() {
        mIsActive = false;
        if (!mViewType)
            mLabelUploader.clear();
        mTileUploader.clear();
        TiledTexture.freeResources();
        for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
            freeSlotContent(i);
        }
    }

    private static interface EntryUpdater {
        public void updateEntry();
    }

    private class AlbumLabelLoader extends BitmapLoader implements EntryUpdater {
        private final int mSlotIndex;
        private final String mTitle;

        public AlbumLabelLoader(int slotIndex, String title) {
            mSlotIndex = slotIndex;
            mTitle = title;
            // mTotalCount = totalCount;
            // mSourceType = sourceType;
        }

        @Override
        protected Future<Bitmap> submitBitmapTask(FutureListener<Bitmap> l) {
            return mThreadPool.submit(mLabelMaker.requestLabel(mTitle), this);
        }

        @Override
        protected void onLoadComplete(Bitmap bitmap) {
            mHandler.obtainMessage(MSG_UPDATE_ALBUM_ENTRY, this).sendToTarget();
        }

        @Override
        public void updateEntry() {
            Bitmap bitmap = getBitmap();
            if (bitmap == null)
                return; // Error or recycled

            AlbumEntry entry = mData[mSlotIndex % mData.length];
            entry.labelTexture = new BitmapTexture(bitmap);
            entry.labelTexture.setOpaque(false);
            // entry.labelTexture = texture;

            if (isActiveSlot(mSlotIndex)) {
                mLabelUploader.addFgTexture(entry.labelTexture);
                --mActiveRequestCount;
                if (mActiveRequestCount == 0)
                    requestNonactiveImages();
                if (mListener != null)
                    mListener.onContentChanged();
            } else {
                mLabelUploader.addBgTexture(entry.labelTexture);
            }
        }
    }

    public void onSlotSizeChanged(int width, int height) {
        if (mSlotWidth == width)
            return;

        isSlotSizeChanged = !mViewType ;
        mSlotWidth = width;
        mLoadingLabel = null;
        mLabelMaker.setLabelWidth(mSlotWidth,KEY_ALBUM);

        if (!mIsActive)
            return;

        for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
            AlbumEntry entry = mData[i % mData.length];
            if (entry.labelLoader != null) {
                entry.labelLoader.recycle();
                entry.labelLoader = null;
                entry.labelTexture = null;
            }
            if (entry.name != null) {
                entry.labelLoader = new AlbumLabelLoader(i, entry.name);
            }
        }
        updateAllImageRequests();
        updateTextureUploadQueue();
    }
}
