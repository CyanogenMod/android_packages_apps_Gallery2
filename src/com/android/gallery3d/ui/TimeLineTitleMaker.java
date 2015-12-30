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
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.android.photos.data.GalleryBitmapPool;
import com.android.gallery3d.R;

import java.util.Locale;

public class TimeLineTitleMaker {

    private final String TAG = "TimelineTitleMaker";
    private static final int BORDER_SIZE = 0;

    private final TimeLineSlotRenderer.LabelSpec mSpec;
    private final TextPaint mTitlePaint;
    private final TextPaint mCountPaint;
    private final Context mContext;
    private final TimeLineSlotView mTimeLineSlotView;

    private final int TIMELINETITLE_START_X = 16;

    public TimeLineTitleMaker(Context context, TimeLineSlotRenderer.LabelSpec spec, TimeLineSlotView slotView) {
        mContext = context;
        mSpec = spec;
        mTimeLineSlotView = slotView;
        mTitlePaint = getTextPaint(spec.timeLineTitleFontSize, spec.timeLineTitleTextColor , true);
        mCountPaint = getTextPaint(spec.timeLineTitleFontSize, spec.timeLineNumberTextColor, true);
    }

    private static TextPaint getTextPaint(
            int textSize, int color, boolean isBold) {
        TextPaint paint = new TextPaint();
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);
        paint.setColor(color);
        paint.setTypeface(Typeface.SANS_SERIF);
        if (isBold) {
            paint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        }
        return paint;
    }

    public ThreadPool.Job<Bitmap> requestTimeLineTitle( String title,
            int photoCount, int videoCount) {
        return new TimeLineTitle(title,photoCount, videoCount, mContext );
    }

    static void drawText(Canvas canvas,
                         int x, int y, String text, int lengthLimit, TextPaint p) {
        text = TextUtils.ellipsize(
                text, p, lengthLimit, TextUtils.TruncateAt.END).toString();
        canvas.drawText(text, x, y - p.getFontMetricsInt().ascent, p);
    }

    private class TimeLineTitle implements ThreadPool.Job<Bitmap> {
        private String mTitle;
        private int mVideoCount;
        private int mImageCount;
        private Context mContext;
        public TimeLineTitle(String title, int imageCount, int videoCount, Context context) {
            mTitle = title;
            mImageCount = imageCount;
            mVideoCount = videoCount;
            mContext = context;

        }

        @Override
        public Bitmap run(JobContext jc) {
            TimeLineSlotRenderer.LabelSpec spec = mSpec;

            Bitmap bitmap;
            int width = mTimeLineSlotView.getTitleWidth();
            int height= spec.timeLineTitleHeight;
            synchronized (this) {
                bitmap = GalleryBitmapPool.getInstance().get(width, height);
            }
            if (bitmap == null) {
                int borders = 2 * BORDER_SIZE;
                bitmap = Bitmap.createBitmap(width + borders,
                        height + borders, Config.ARGB_8888);
            }
            if (bitmap == null) {
                return null;
            }
            Canvas canvas = new Canvas(bitmap);
            canvas.clipRect(BORDER_SIZE, BORDER_SIZE,
                    bitmap.getWidth() - BORDER_SIZE,
                    bitmap.getHeight() - BORDER_SIZE);
            canvas.drawColor(mSpec.timeLineTitleBackgroundColor, PorterDuff.Mode.SRC);

            canvas.translate(BORDER_SIZE, BORDER_SIZE);


            StringBuilder sb = new StringBuilder();
            if(mImageCount != 0) {
                sb.append(mContext.getResources().getQuantityString(R.plurals.number_of_photos, mImageCount, mImageCount));
                if(mVideoCount!=0) {
                    sb.append("  " +mContext.getResources().getQuantityString(
                             R.plurals.number_of_videos, mVideoCount, mVideoCount));
                }
            } else {
                if(mVideoCount != 0) {
                    sb.append(mContext.getResources().getQuantityString(
                            R.plurals.number_of_videos, mVideoCount, mVideoCount));
                }
            }
            String countString = sb.toString();

            if (jc.isCancelled()) return null;


            int x;
            int y = 0;
            if (mTitle != null) {
                mTitle = mTitle.toUpperCase();
                x = TIMELINETITLE_START_X;
                y = (height - spec.timeLineTitleFontSize)/2;
                // re-calculate x for RTL
                if (View.LAYOUT_DIRECTION_RTL == TextUtils
                        .getLayoutDirectionFromLocale(Locale.getDefault())) {
                    Rect titleBounds = new Rect();
                    mTitlePaint.getTextBounds(
                        mTitle, 0, mTitle.length(), titleBounds);
                    int w = titleBounds.width();
                    x = width - mTitle.length() - w;
                }
                drawText(canvas, x, y, mTitle, width-x, mTitlePaint);
            }

            if (countString != null) {

                Rect mediaCountBounds = new Rect();
                mCountPaint.getTextBounds(
                        countString, 0, countString.length(), mediaCountBounds);
                int w = mediaCountBounds.width();
                y = (height - spec.timeLineTitleFontSize)/2;
                x = width - countString.length() -w;
                // re-calculate x for RTL
                if (View.LAYOUT_DIRECTION_RTL == TextUtils
                        .getLayoutDirectionFromLocale(Locale.getDefault())) {
                    x = TIMELINETITLE_START_X;
                }
                drawText(canvas, x, y, countString,
                        width - x, mCountPaint);
            }
            return bitmap;
        }
    }
}
