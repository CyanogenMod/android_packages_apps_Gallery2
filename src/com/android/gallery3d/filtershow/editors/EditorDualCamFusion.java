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

package com.android.gallery3d.filtershow.editors;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.filters.FilterDualCamFusionRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.imageshow.ImageFusion;
import com.android.gallery3d.filtershow.imageshow.MasterImage;

public class EditorDualCamFusion extends ImageOnlyEditor {
    public static final String TAG = EditorDualCamFusion.class.getSimpleName();
    public static final int ID = R.id.editorDualCamFusion;

    protected ImageFusion mImageFusion;
    private Button mPickUnderlayBtn;
    private OnClickListener mPickBtnClickListener = new OnClickListener() {
        @Override
        public void onClick(View arg0) {
            MasterImage.getImage().getActivity().pickImage(FilterShowActivity.SELECT_FUSION_UNDERLAY);
        }
    };

    public EditorDualCamFusion() {
        super(ID);
    }

    public boolean useUtilityPanel() {
        return true;
    }

    @Override
    public void createEditor(Context context, FrameLayout frameLayout) {
        super.createEditor(context, frameLayout);
        if (mImageFusion == null) {
            mImageFusion = new ImageFusion(context);
        }
        mView = mImageShow = mImageFusion;
        mImageFusion.setEditor(this);
    }

    @Override
    public void setUtilityPanelUI(View actionButton, View editControl) {
        editControl.setVisibility(View.GONE);
    }

    @Override
    public void openUtilityPanel(final LinearLayout accessoryViewList) {
        super.openUtilityPanel(accessoryViewList);
        mPickUnderlayBtn = (Button) accessoryViewList.findViewById(R.id.applyEffect);
        updateText();
    }

    public void setUnderlayImageUri(Uri uri) {
        FilterRepresentation filter = getLocalRepresentation();
        if(filter instanceof FilterDualCamFusionRepresentation) {
            mImageFusion.setUnderlay(uri);
            commitLocalRepresentation();
        }
    }

    private void updateEffectButton(boolean enabled) {
        mPickUnderlayBtn.setEnabled(enabled);
        if(enabled) {
            mPickUnderlayBtn.setOnClickListener(mPickBtnClickListener);
            mPickUnderlayBtn.setText(R.string.fusion_pick_underlay);
        } else {
            mPickUnderlayBtn.setOnClickListener(null);
            mPickUnderlayBtn.setText(R.string.fusion_pick_point);
        }
    }


    protected void updateText() {
        if(mPickUnderlayBtn != null) {
            updateEffectButton(hasSegment());
        }
    }

    private boolean hasSegment() {
        FilterRepresentation filter = getLocalRepresentation();
        if(filter instanceof FilterDualCamFusionRepresentation) {
            return !((FilterDualCamFusionRepresentation) filter).getPoint().equals(-1,-1);
        }
        return false;
    }

    @Override
    public void reflectCurrentFilter() {
        super.reflectCurrentFilter();
        FilterRepresentation rep = getLocalRepresentation();
        if (rep != null && rep instanceof FilterDualCamFusionRepresentation) {
            FilterDualCamFusionRepresentation dualRep = (FilterDualCamFusionRepresentation) rep;
            mImageFusion.setRepresentation(dualRep);
        }
    }
}
