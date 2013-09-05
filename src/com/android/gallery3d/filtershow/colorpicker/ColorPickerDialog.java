/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.gallery3d.filtershow.colorpicker;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ToggleButton;

import com.android.gallery3d.R;

public class ColorPickerDialog extends Dialog   {
    ToggleButton mSelectedButton;
    GradientDrawable mSelectRect;
    ColorHueView mColorHueView;
    ColorSVRectView mColorSVRectView;
    ColorOpacityView mColorOpacityView;
    float[] mHSVO = new float[4]; // hue=0..360, sat & val opacity = 0...1

    public ColorPickerDialog(Context context, final ColorListener cl) {
        super(context);
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm =  (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        int height = metrics.heightPixels*8/10;
        int width = metrics.widthPixels*8/10;
        getWindow().setLayout(width, height);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.filtershow_color_picker);
        mColorHueView = (ColorHueView) findViewById(R.id.ColorHueView);
        mColorSVRectView = (ColorSVRectView) findViewById(R.id.colorRectView);
        mColorOpacityView = (ColorOpacityView) findViewById(R.id.colorOpacityView);
        float[] hsvo = new float[] {
                123, .9f, 1, 1 };

        mSelectRect = (GradientDrawable) getContext()
                .getResources().getDrawable(R.drawable.filtershow_color_picker_roundrect);
        Button selButton = (Button) findViewById(R.id.btnSelect);
        selButton.setCompoundDrawablesWithIntrinsicBounds(null, null, mSelectRect, null);
        Button sel = (Button) findViewById(R.id.btnSelect);

        sel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ColorPickerDialog.this.dismiss();
                if (cl != null) {
                    cl.setColor(mHSVO);
                }
            }
        });

        mColorSVRectView.setColor(hsvo);
        mColorOpacityView.setColor(hsvo);
        mColorHueView.setColor(hsvo);
        mColorHueView.addColorListener(mColorSVRectView);
        mColorSVRectView.addColorListener(mColorHueView);
        mColorHueView.addColorListener(mColorOpacityView);
        mColorSVRectView.addColorListener(mColorOpacityView);
        mColorOpacityView.addColorListener(mColorSVRectView);
        mColorOpacityView.addColorListener(mColorHueView);
        ColorListener colorListener = new ColorListener(){

            @Override
            public void setColor(float[] hsvo) {
                System.arraycopy(hsvo, 0, mHSVO, 0, mHSVO.length);
                int color = Color.HSVToColor(hsvo);
                mSelectRect.setColor(color);
                setButtonColor(mSelectedButton, hsvo);
            }
        };
        mColorOpacityView.addColorListener(colorListener);
        mColorHueView.addColorListener(colorListener);
        mColorSVRectView.addColorListener(colorListener);

    }

    void toggleClick(ToggleButton v, int[] buttons, boolean isChecked) {
        int id = v.getId();
        if (!isChecked) {
            mSelectedButton = null;
            return;
        }
        for (int i = 0; i < buttons.length; i++) {
            if (id != buttons[i]) {
                ToggleButton b = (ToggleButton) findViewById(buttons[i]);
                b.setChecked(false);
            }
        }
        mSelectedButton = v;

        float[] hsv = (float[]) v.getTag();

        ColorHueView csv = (ColorHueView) findViewById(R.id.ColorHueView);
        ColorSVRectView cwv = (ColorSVRectView) findViewById(R.id.colorRectView);
        ColorOpacityView cvv = (ColorOpacityView) findViewById(R.id.colorOpacityView);
        cwv.setColor(hsv);
        cvv.setColor(hsv);
        csv.setColor(hsv);
    }


    public void setColor(float[] hsvo) {
        mColorOpacityView.setColor(hsvo);
        mColorHueView.setColor(hsvo);
        mColorSVRectView.setColor(hsvo);
    }

    private void setButtonColor(ToggleButton button, float[] hsv) {
        if (button == null) {
            return;
        }
        int color = Color.HSVToColor(hsv);
        button.setBackgroundColor(color);
        float[] fg = new float[] {
                (hsv[0] + 180) % 360,
                hsv[1],
                        (hsv[2] > .5f) ? .1f : .9f
        };
        button.setTextColor(Color.HSVToColor(fg));
        button.setTag(hsv);
    }

}
