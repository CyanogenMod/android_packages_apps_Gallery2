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

package com.android.gallery3d.filtershow.editors;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.controller.BitmapCaller;
import com.android.gallery3d.filtershow.controller.FilterView;
import com.android.gallery3d.filtershow.filters.FilterDrawRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.ImageFilterDraw;
import com.android.gallery3d.filtershow.imageshow.ImageDraw;

public class EditorDraw extends ParametricEditor implements FilterView {
    private static final String LOGTAG = "EditorDraw";
    public static final int ID = R.id.editorDraw;
    public ImageDraw mImageDraw;
    private static final int MODE_BRIGHTNESS = FilterDrawRepresentation.PARAM_BRIGHTNESS;
    private static final int MODE_SATURATION = FilterDrawRepresentation.PARAM_SATURATION;
    private static final int MODE_SIZE = FilterDrawRepresentation.PARAM_SIZE;
    private static final int MODE_HUEE = FilterDrawRepresentation.PARAM_HUE;
    private static final int MODE_SIZEE = FilterDrawRepresentation.PARAM_SIZE;
    private static final int MODE_OPACITY = FilterDrawRepresentation.PARAM_OPACITY;
    private static final int MODE_STYLE = FilterDrawRepresentation.PARAM_STYLE;
    int[] brushIcons = {
            R.drawable.brush_flat,
            R.drawable.brush_round,
            R.drawable.brush_gauss,
            R.drawable.brush_marker,
            R.drawable.brush_spatter
    };

    String mParameterString;

    public EditorDraw() {
        super(ID);
    }

    @Override
    public String calculateUserMessage(Context context, String effectName, Object parameterValue) {
        FilterDrawRepresentation rep = getDrawRep();
        if (rep == null) {
            return "";
        }
        String paramString;
        String val = rep.getValueString();

        mImageDraw.displayDrawLook();
        return mParameterString + val;
    }

    @Override
    public void createEditor(Context context, FrameLayout frameLayout) {
        mView = mImageShow = mImageDraw = new ImageDraw(context);
        super.createEditor(context, frameLayout);
        mImageDraw.setEditor(this);

    }

    @Override
    public void reflectCurrentFilter() {
        super.reflectCurrentFilter();
        FilterRepresentation rep = getLocalRepresentation();
        if (rep != null && getLocalRepresentation() instanceof FilterDrawRepresentation) {
            FilterDrawRepresentation drawRep = (FilterDrawRepresentation) getLocalRepresentation();
            mImageDraw.setFilterDrawRepresentation(drawRep);
            drawRep.getParam(FilterDrawRepresentation.PARAM_STYLE).setFilterView(this);
            drawRep.setPramMode(FilterDrawRepresentation.PARAM_HUE);
            mParameterString = mContext.getString(R.string.draw_hue);
            control(drawRep.getCurrentParam(), mEditControl);
        }
    }

    @Override
    public void openUtilityPanel(final LinearLayout accessoryViewList) {
        Button view = (Button) accessoryViewList.findViewById(R.id.applyEffect);
        view.setText(mContext.getString(R.string.draw_hue));
        view.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                showPopupMenu(accessoryViewList);
            }
        });
    }

    @Override
    public boolean showsSeekBar() {
        return false;
    }

    private void showPopupMenu(LinearLayout accessoryViewList) {
        final Button button = (Button) accessoryViewList.findViewById(
                R.id.applyEffect);
        if (button == null) {
            return;
        }
        final PopupMenu popupMenu = new PopupMenu(mImageShow.getActivity(), button);
        popupMenu.getMenuInflater().inflate(R.menu.filtershow_menu_draw, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                selectMenuItem(item);
                return true;
            }
        });
        popupMenu.show();

    }

    protected void selectMenuItem(MenuItem item) {
        ImageFilterDraw filter = (ImageFilterDraw) mImageShow.getCurrentFilter();
        FilterDrawRepresentation rep = getDrawRep();
        if (rep == null) {
            return;
        }

        switch (item.getItemId()) {
            case R.id.draw_menu_clear:
                ImageDraw idraw = (ImageDraw) mImageShow;
                idraw.resetParameter();
                commitLocalRepresentation();
                break;
            case R.id.draw_menu_hue:
                rep.setPramMode(FilterDrawRepresentation.PARAM_HUE);
                break;
            case R.id.draw_menu_opacity:
                rep.setPramMode(FilterDrawRepresentation.PARAM_OPACITY);
                break;
            case R.id.draw_menu_saturation:
                rep.setPramMode(FilterDrawRepresentation.PARAM_SATURATION);
                break;
            case R.id.draw_menu_size:
                rep.setPramMode(FilterDrawRepresentation.PARAM_SIZE);
                break;
            case R.id.draw_menu_style:
                rep.setPramMode(FilterDrawRepresentation.PARAM_STYLE);
                break;
            case R.id.draw_menu_value:
                rep.setPramMode(FilterDrawRepresentation.PARAM_BRIGHTNESS);
                break;
        }
        if (item.getItemId() != R.id.draw_menu_clear) {
            mParameterString = item.getTitle().toString();
        }
        control(rep.getCurrentParam(), mEditControl);
        mControl.updateUI();
        mView.invalidate();
    }

    FilterDrawRepresentation getDrawRep() {
        FilterRepresentation rep = getLocalRepresentation();
        if (rep instanceof FilterDrawRepresentation) {
            return (FilterDrawRepresentation) rep;
        }
        return null;
    }

    @Override
    public void computeIcon(int index, BitmapCaller caller) {
        Bitmap bitmap = BitmapFactory.decodeResource(mContext.getResources(), brushIcons[index]);
        caller.available(bitmap);
    }

    public int getBrushIcon(int type){
       return  brushIcons[type];
    }

}
