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

package com.android.gallery3d.data;

import android.drm.DrmManagerClient;
import android.drm.DrmStore.Action;
import android.drm.DrmStore.RightsStatus;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.data.BytesBufferPool.BytesBuffer;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;

abstract class ImageCacheRequest implements Job<Bitmap> {
    private static final String TAG = "ImageCacheRequest";

    protected GalleryApp mApplication;
    private Path mPath;
    private int mType;
    private int mTargetSize;
    private String mFilePath;
    private String mMimeType;
    private long mTimeModified;

    public ImageCacheRequest(GalleryApp application,
            Path path, long timeModified, int type, int targetSize, String filePath, String mimetype) {
        mApplication = application;
        mPath = path;
        mType = type;
        mTargetSize = targetSize;
        mFilePath = filePath;
        mMimeType = mimetype;
        mTimeModified = timeModified;
    }

    private String debugTag() {
        return mPath + "," + mTimeModified + "," +
                ((mType == MediaItem.TYPE_THUMBNAIL) ? "THUMB" :
                (mType == MediaItem.TYPE_MICROTHUMBNAIL) ? "MICROTHUMB" : "?");
    }

    @Override
    public Bitmap run(JobContext jc) {
        ImageCacheService cacheService = mApplication.getImageCacheService();

        if (mFilePath != null && mFilePath.endsWith(".dcf")) {
            DrmManagerClient drmClient = new DrmManagerClient(mApplication.getAndroidContext());
            mFilePath = mFilePath.replace("/storage/emulated/0", "/storage/emulated/legacy");

            // This hack is added to work FL. It will remove after the sdcard permission issue solved
            int statusDisplay = drmClient.checkRightsStatus(mFilePath, Action.DISPLAY);
            statusDisplay = RightsStatus.RIGHTS_VALID;
            int statusPlay = drmClient.checkRightsStatus(mFilePath, Action.PLAY);
            statusPlay = RightsStatus.RIGHTS_VALID;

           if (mMimeType == null) {
                if ((RightsStatus.RIGHTS_VALID != statusDisplay)
                                && (RightsStatus.RIGHTS_VALID != statusPlay)) {
                    return null;
                }
            } else if (mMimeType.startsWith("video/")
                    && RightsStatus.RIGHTS_VALID != statusPlay) {
                return null;
            } else if (mMimeType.startsWith("image/")
                    && RightsStatus.RIGHTS_VALID != statusDisplay) {
                return null;
            }
            if (drmClient != null) drmClient.release();
        }

        BytesBuffer buffer = MediaItem.getBytesBufferPool().get();
        try {
            boolean found = cacheService.getImageData(mPath, mTimeModified, mType, buffer);
            if (jc.isCancelled()) return null;
            if (found) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bitmap;
                if (mType == MediaItem.TYPE_MICROTHUMBNAIL) {
                    bitmap = DecodeUtils.decodeUsingPool(jc,
                            buffer.data, buffer.offset, buffer.length, options);
                } else {
                    bitmap = DecodeUtils.decodeUsingPool(jc,
                            buffer.data, buffer.offset, buffer.length, options);
                }
                if (bitmap == null && !jc.isCancelled()) {
                    Log.w(TAG, "decode cached failed " + debugTag());
                }
                return bitmap;
            }
        } finally {
            MediaItem.getBytesBufferPool().recycle(buffer);
        }
        Bitmap bitmap = onDecodeOriginal(jc, mType);
        if (jc.isCancelled()) return null;

        if (bitmap == null) {
            Log.w(TAG, "decode orig failed " + debugTag());
            return null;
        }

        if (mType == MediaItem.TYPE_MICROTHUMBNAIL) {
            bitmap = BitmapUtils.resizeAndCropCenter(bitmap, mTargetSize, true);
        } else {
            bitmap = BitmapUtils.resizeDownBySideLength(bitmap, mTargetSize, true);
        }
        if (jc.isCancelled()) return null;

        byte[] array = BitmapUtils.compressToBytes(bitmap);
        if (jc.isCancelled()) return null;

        cacheService.putImageData(mPath, mTimeModified, mType, array);
        return bitmap;
    }

    public abstract Bitmap onDecodeOriginal(JobContext jc, int targetSize);
}
