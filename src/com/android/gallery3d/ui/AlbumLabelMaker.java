/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 * Not a Contribution
 *
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

package com.android.gallery3d.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import com.android.gallery3d.R;
import com.android.gallery3d.data.DataSourceType;
import com.android.photos.data.GalleryBitmapPool;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ThreadPool.JobContext;

import java.util.Locale;

public class AlbumLabelMaker {
    private static final int BORDER_SIZE = 0;

    private AlbumSetSlotRenderer.LabelSpec mSpec;
    private AlbumSlotRenderer.LabelSpec mAlbumListSpec;
    private TextPaint mTitlePaint;
    private TextPaint mCountPaint;
    private final Context mContext;

    private int mLabelWidth;
    private int mBitmapWidth;
    private int mBitmapHeight;

    /*private final LazyLoadedBitmap mLocalSetIcon;
    private final LazyLoadedBitmap mPicasaIcon;
    private final LazyLoadedBitmap mCameraIcon;*/

    public AlbumLabelMaker(Context context, AlbumSetSlotRenderer.LabelSpec spec) {
        mContext = context;
        mSpec = spec;
        mTitlePaint = getTextPaint(spec.titleFontSize, spec.titleColor, false);
        mCountPaint = getTextPaint(spec.countFontSize, spec.countColor, false);

        /*mLocalSetIcon = new LazyLoadedBitmap(R.drawable.frame_overlay_gallery_folder);
        mPicasaIcon = new LazyLoadedBitmap(R.drawable.frame_overlay_gallery_picasa);
        mCameraIcon = new LazyLoadedBitmap(R.drawable.frame_overlay_gallery_camera);*/
    }

    public AlbumLabelMaker(Context context, AlbumSlotRenderer.LabelSpec spec) {
        mContext = context;
        mAlbumListSpec = spec;
        mTitlePaint = getTextPaint(spec.titleFontSize, spec.titleColor, false);
    }

    public static int getBorderSize() {
        return BORDER_SIZE;
    }

    /*private Bitmap getOverlayAlbumIcon(int sourceType) {
        switch (sourceType) {
            case DataSourceType.TYPE_CAMERA:
                return mCameraIcon.get();
            case DataSourceType.TYPE_LOCAL:
                return mLocalSetIcon.get();
            case DataSourceType.TYPE_PICASA:
                return mPicasaIcon.get();
        }
        return null;
    }*/

    private static TextPaint getTextPaint(int textSize, int color, boolean isBold) {
        TextPaint paint = new TextPaint();
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);
        paint.setColor(color);
        paint.setTypeface(Typeface.SANS_SERIF);
        //paint.setShadowLayer(2f, 0f, 0f, Color.LTGRAY);
        if (isBold) {
            paint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        }
        return paint;
    }

    private class LazyLoadedBitmap {
        private Bitmap mBitmap;
        private int mResId;

        public LazyLoadedBitmap(int resId) {
            mResId = resId;
        }

        public synchronized Bitmap get() {
            if (mBitmap == null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                mBitmap = BitmapFactory.decodeResource(
                        mContext.getResources(), mResId, options);
            }
            return mBitmap;
        }
    }

    public synchronized void setLabelWidth(int width, String key) {
        if (mLabelWidth == width) return;
        mLabelWidth = width;
        int borders = 2 * BORDER_SIZE;
        mBitmapWidth = width + borders;
        if (key.equalsIgnoreCase(AlbumSetSlidingWindow.KEY_ALBUM)) {
            mBitmapHeight = mSpec.labelBackgroundHeight + borders;
        } else {
            mBitmapHeight = mAlbumListSpec.labelBackgroundHeight + borders;
        }
    }

    public ThreadPool.Job<Bitmap> requestLabel(
            String title, String count, int sourceType) {
        return new AlbumLabelJob(title, count, sourceType);
    }

    public ThreadPool.Job<Bitmap> requestLabel(String title) {
        return new AlbumLabelJob(title);
    }

    static void drawText(Canvas canvas,
            int x, int y, String text, int lengthLimit, TextPaint p) {
        // The TextPaint cannot be used concurrently
        synchronized (p) {
            text = TextUtils.ellipsize(
                    text, p, lengthLimit, TextUtils.TruncateAt.END).toString();
            canvas.drawText(text, x, y - p.getFontMetricsInt().ascent, p);
        }
    }

    private class AlbumLabelJob implements ThreadPool.Job<Bitmap> {
        private final String mTitle;
        private String mCount;
        private int mSourceType;
        private boolean isAlbumListViewShown;

        public AlbumLabelJob(String title, String count, int sourceType) {
            mTitle = title;
            mCount = count;
            mSourceType = sourceType;
        }

        public AlbumLabelJob(String title) {
            mTitle = title;
            isAlbumListViewShown = true;

        }

        @Override
        public Bitmap run(JobContext jc) {
            AlbumSetSlotRenderer.LabelSpec s = mSpec;
            AlbumSlotRenderer.LabelSpec s1 = mAlbumListSpec;

            String title = mTitle;
            String count = mCount;
            //Bitmap icon = getOverlayAlbumIcon(mSourceType);

            Bitmap bitmap;
            int labelWidth;

            synchronized (this) {
                labelWidth = mLabelWidth;
                bitmap = GalleryBitmapPool.getInstance().get(mBitmapWidth, mBitmapHeight);
            }

            int borders = 2 * BORDER_SIZE;
            if (!isAlbumListViewShown) {
                if (bitmap == null) {

                    bitmap = Bitmap
                            .createBitmap(labelWidth + borders,
                                    s.labelBackgroundHeight + borders,
                                    Config.ARGB_8888);
                }
            } else {
                bitmap = Bitmap.createBitmap(labelWidth + borders,
                        s1.labelBackgroundHeight + borders, Config.ARGB_8888);
            }

            Canvas canvas = new Canvas(bitmap);
            canvas.clipRect(BORDER_SIZE, BORDER_SIZE,
                    bitmap.getWidth() - BORDER_SIZE,
                    bitmap.getHeight() - BORDER_SIZE);
            if (!isAlbumListViewShown) {
                 canvas.drawColor(mSpec.backgroundColor, PorterDuff.Mode.SRC);
            }

            canvas.translate(BORDER_SIZE, BORDER_SIZE);

            if (View.LAYOUT_DIRECTION_RTL == TextUtils
                    .getLayoutDirectionFromLocale(Locale.getDefault())) {// RTL
                // draw title
                if (jc.isCancelled()) return null;
                int strLength = (int) mTitlePaint.measureText(title);
                if (!isAlbumListViewShown) {
                    int x = labelWidth - s.leftMargin - strLength;
                    // TODO: is the offset relevant in new reskin?
                    // int y = s.titleOffset;
                    int y = (s.labelBackgroundHeight - s.titleFontSize) / 2;
                    drawText(canvas, x, y, title, labelWidth - s.leftMargin - x, mTitlePaint);

                    // draw count
                    if (jc.isCancelled()) return null;
                    x = s.leftMargin + 10;// plus 10 to get a much bigger margin
                    y = (s.labelBackgroundHeight - s.countFontSize) / 2;
                    drawText(canvas, x, y, count, labelWidth - x, mCountPaint);
                } else {
                    int x = labelWidth
                            - (s1.leftMargin + s1.iconSize)
                            - strLength;
                    // TODO: is the offset relevant in new reskin?
                    // int y = s.titleOffset;
                    int y = (s1.labelBackgroundHeight - s1.titleFontSize) / 2;
                    drawText(canvas, x, y, title, labelWidth - s1.leftMargin
                            - x, mTitlePaint);
                }

            } else { // LTR
                                // draw title
                if (jc.isCancelled())
                    return null;
                if (!isAlbumListViewShown) {
                    int x = s.leftMargin + s.titleLeftMargin;
                    // TODO: is the offset relevant in new reskin?
                    // int y = s.titleOffset;
                    int y = (s.labelBackgroundHeight - s.titleFontSize) / 2;
                    drawText(canvas, x, y, title, labelWidth - s.leftMargin - x
                            - s.titleRightMargin - s.countRightMargin, mTitlePaint);

                    // draw count
                    if (jc.isCancelled())
                        return null;
                    x = labelWidth - s.titleRightMargin - s.countRightMargin;
                    y = (s.labelBackgroundHeight - s.countFontSize) / 2;
                    drawText(canvas, x, y, count, labelWidth - x, mCountPaint);
                } else {

                    int x = s1.leftMargin + s1.iconSize;
                    // TODO: is the offset relevant in new reskin?
                    // int y = s.titleOffset;
                    int y = (s1.labelBackgroundHeight - s1.titleFontSize) / 2;

                    drawText(canvas, x, y, title, labelWidth - s1.leftMargin
                            - x, mTitlePaint);
                }
          }
            return bitmap;
        }
    }

    public void recycleLabel(Bitmap label) {
        GalleryBitmapPool.getInstance().put(label);
    }
}
