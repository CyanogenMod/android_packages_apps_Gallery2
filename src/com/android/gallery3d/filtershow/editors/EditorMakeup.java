/*
* Copyright (C) 2014,2015 Thundersoft Corporation
* All rights Reserved
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

package com.android.gallery3d.filtershow.editors;

import android.view.View;
import android.widget.SeekBar;

import com.android.gallery3d.R;

public class EditorMakeup extends BasicEditor {
    public static int ID = R.id.editorMakeup;
    private final String LOGTAG = "EditorMakeup";

    public EditorMakeup() {
        super(ID, R.layout.filtershow_default_editor, R.id.basicEditor);
    }

    @Override
    public void setUtilityPanelUI(View actionButton, View editControl) {
        super.setUtilityPanelUI(actionButton, editControl);
        mSeekBar = (SeekBar) editControl.findViewById(R.id.primarySeekBar);
        if (mSeekBar != null) {
            mSeekBar.setVisibility(View.INVISIBLE);
        }
    }



}
