/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.

 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.gallery3d.app;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

public class MovieControllerOverlayNew extends MovieControllerOverlay {

    public MovieControllerOverlayNew(Context context) {
        super(context);
    }

    @Override
    protected void createTimeBar(Context context) {
        mTimeBar = new TimeBarNew(context, this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            cancelHiding();
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            maybeStartHiding();
            return true;
        } else {
            return super.onTouchEvent(event);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = ((MovieActivity) mContext).getWindowManager().getDefaultDisplay().getWidth();
        Rect insets = mWindowInsets;
        int pr = insets.right;
        int pb = insets.bottom;

        int h = bottom - top;
        int w = right - left;

        int y = h - pb;

        mBackground.layout(0, y - mTimeBar.getPreferredHeight(), w, y);
        mScreenModeExt.onLayout(w, pr, y);

        mPlayPauseReplayView.layout(0, y - mTimeBar.getPreferredHeight(),
                mTimeBar.getPreferredHeight(), y);
        mTimeBar.layout(mTimeBar.getPreferredHeight(), y - mTimeBar.getPreferredHeight(),
                width - mScreenModeExt.getAddedRightPadding(), y);
    }

}
