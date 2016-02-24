/*
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
import android.graphics.Rect;

import com.android.gallery3d.R;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.glrenderer.FadeOutTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.NinePatchTexture;
import com.android.gallery3d.glrenderer.ResourceTexture;
import com.android.gallery3d.glrenderer.Texture;

public abstract class AbstractSlotRenderer implements SlotView.SlotRenderer {

    private final ResourceTexture mVideoOverlay;
    private final ResourceTexture mVideoPlayIcon;
    private final ResourceTexture mPanoramaIcon;
    private final NinePatchTexture mFramePressed;
    private final NinePatchTexture mFrameSelected;
    private final ResourceTexture mDrmIcon;
    private final ResourceTexture mSelectionIcon;
    private FadeOutTexture mFramePressedUp;

    protected AbstractSlotRenderer(Context context) {
        mVideoOverlay = new ResourceTexture(context, R.drawable.ic_video_thumb);
        mVideoPlayIcon = new ResourceTexture(context, R.drawable.play_detail);
        mPanoramaIcon = new ResourceTexture(context, R.drawable.ic_360pano_holo_light);
        mFramePressed = new NinePatchTexture(context, R.drawable.grid_pressed);
        mFrameSelected = new NinePatchTexture(context, R.drawable.grid_selected);
        mDrmIcon = new ResourceTexture(context, R.drawable.drm_image);
        mSelectionIcon = new ResourceTexture(context, R.drawable.multiselect);
    }

    protected void drawContent(GLCanvas canvas,
            Texture content, int width, int height, int rotation) {
        canvas.save(GLCanvas.SAVE_FLAG_MATRIX);

        // The content is always rendered in to the largest square that fits
        // inside the slot, aligned to the top of the slot.
        //width = height = Math.min(width, height);
        if (rotation != 0) {
            canvas.translate(width / 2, height / 2);
            canvas.rotate(rotation, 0, 0, 1);
            canvas.translate(-width / 2, -height / 2);
        }

        // Fit the content into the box
        float scale = Math.min(
                (float) width / content.getWidth(),
                (float) height / content.getHeight());
        canvas.scale(scale, scale, 1);
        content.draw(canvas, 0, 0);

        canvas.restore();
    }

    protected void drawVideoOverlay(GLCanvas canvas, int width, int height,
            boolean isGridViewShown, int thumbSize) {
        // Scale the video overlay to the height of the thumbnail and put it
        // on the left side.
        int side = Math.min(width, height) / 6;
        if (!isGridViewShown)
            width = thumbSize;
        mVideoPlayIcon.draw(canvas, width / 8 - side / 2, height * 7 / 8 - side / 2, side, side);
    }

    protected void drawDrmOverlay(GLCanvas canvas, int width, int height, int Drm_mediaType,
                boolean isGridViewShown, int thumbSize) {
        // Scale the video overlay to the height of the thumbnail and put it on the left side.
        if (Drm_mediaType == MediaObject.MEDIA_TYPE_DRM_VIDEO) {
            ResourceTexture v = mVideoOverlay;
            float scale = (float) height / v.getHeight();
            int w = Math.round(scale * v.getWidth());
            int h = Math.round(scale * v.getHeight());
            v.draw(canvas, 0, 0, w, h);
        }
        int side = Math.min(width, height) / 6;
        if (!isGridViewShown) {
            width = thumbSize;
        }
        mDrmIcon.draw(canvas, (width - side) / 2, (height - side) / 2, side, side);
    }

    protected void drawPanoramaIcon(GLCanvas canvas, int width, int height,
                   boolean isGridViewShown, int thumbSize) {
        int iconSize = Math.min(width, height) / 6;
        if (!isGridViewShown) {
            width = thumbSize;
        }
        mPanoramaIcon.draw(canvas, (width - iconSize) / 2, (height - iconSize) / 2,
                iconSize, iconSize);
    }

    protected boolean isPressedUpFrameFinished() {
        if (mFramePressedUp != null) {
            if (mFramePressedUp.isAnimating()) {
                return false;
            } else {
                mFramePressedUp = null;
            }
        }
        return true;
    }

    protected void drawPressedUpFrame(GLCanvas canvas, int width, int height) {
        if (mFramePressedUp == null) {
            mFramePressedUp = new FadeOutTexture(mFramePressed);
        }
        drawFrame(canvas, mFramePressed.getPaddings(), mFramePressedUp, 0, 0, width, height);
    }

    protected void drawPressedFrame(GLCanvas canvas, int width, int height) {
        drawFrame(canvas, mFramePressed.getPaddings(), mFramePressed, 0, 0, width, height);
    }

    protected void drawSelectedFrame(GLCanvas canvas, int width, int height) {
         mSelectionIcon.draw(canvas,15,15);
        //drawFrame(canvas, mFrameSelected.getPaddings(), mFrameSelected, 0, 0, width, height);
    }

    protected static void drawFrame(GLCanvas canvas, Rect padding, Texture frame,
            int x, int y, int width, int height) {
        frame.draw(canvas, x - padding.left, y - padding.top, width + padding.left + padding.right,
                 height + padding.top + padding.bottom);
    }
}
