/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
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

package com.android.gallery3d.filtershow.imageshow;

import android.content.Context;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import com.android.gallery3d.filtershow.editors.Editor;
import com.android.gallery3d.filtershow.filters.FilterDualCamBasicRepresentation;

public class ImageDualCamera extends ImageShow {
    private static final String LOGTAG = "ImageDualCamera";
    protected Editor mEditor;
    protected FilterDualCamBasicRepresentation mRepresentation;
    private Matrix mToOrig;
    private float[] mTmpPoint = new float[2];

    public ImageDualCamera(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageDualCamera(Context context) {
        super(context);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent event) {
        Log.e(LOGTAG, "single tap: " + event.getX() + "x" + event.getY());
        calcScreenMapping();
        mTmpPoint[0] = event.getX();
        mTmpPoint[1] = event.getY();
        mToOrig.mapPoints(mTmpPoint);

        mRepresentation.setPoint((int)mTmpPoint[0], (int)mTmpPoint[1]);
        mEditor.commitLocalRepresentation();
        return true;
    }

    public void setEditor(Editor editor) {
        mEditor = editor;
    }

    public void setRepresentation(FilterDualCamBasicRepresentation representation) {
        mRepresentation = representation;
    }

    private void calcScreenMapping() {
        mToOrig = getScreenToImageMatrix(true);
    }
}
