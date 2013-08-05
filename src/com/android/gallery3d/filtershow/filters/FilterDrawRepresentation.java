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

package com.android.gallery3d.filtershow.filters;

import android.graphics.Color;
import android.graphics.Path;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.controller.BasicParameterInt;
import com.android.gallery3d.filtershow.controller.BasicParameterStyle;
import com.android.gallery3d.filtershow.controller.Parameter;
import com.android.gallery3d.filtershow.controller.ParameterBrightness;
import com.android.gallery3d.filtershow.controller.ParameterHue;
import com.android.gallery3d.filtershow.controller.ParameterOpacity;
import com.android.gallery3d.filtershow.controller.ParameterSaturation;
import com.android.gallery3d.filtershow.editors.EditorDraw;

import java.util.Vector;

public class FilterDrawRepresentation extends FilterRepresentation {
    private static final String LOGTAG = "FilterDrawRepresentation";

    public static final int PARAM_SIZE = 0;
    public static final int PARAM_HUE = 1;
    public static final int PARAM_BRIGHTNESS = 2;
    public static final int PARAM_SATURATION = 3;
    public static final int PARAM_OPACITY = 4;
    public static final int PARAM_STYLE = 5;
    private BasicParameterInt mParamSize = new BasicParameterInt(PARAM_SIZE, 20, 2, 300);
    private BasicParameterInt mParamHue = new ParameterHue(PARAM_HUE, 0);
    private BasicParameterInt mParamBrightness = new ParameterBrightness(PARAM_BRIGHTNESS, 220);
    private BasicParameterInt mParamSaturation = new ParameterSaturation(PARAM_SATURATION, 200);
    private ParameterOpacity mParamOpacity = new ParameterOpacity(PARAM_OPACITY, 200);
    private BasicParameterStyle mParamStyle = new BasicParameterStyle(PARAM_STYLE, 3);
    int mParamMode;
    Parameter mCurrentParam = mParamSize;

    private Parameter[] mAllParam = {
            mParamSize,
            mParamHue,
            mParamBrightness,
            mParamSaturation,
            mParamOpacity,
            mParamStyle
    };

    public void setPramMode(int mode) {
        mParamMode = mode;
        mCurrentParam = mAllParam[mParamMode];
    }

    public Parameter getCurrentParam() {
        return  mAllParam[mParamMode];
    }

    public Parameter getParam(int type) {
        return  mAllParam[type];
    }

    public static class StrokeData implements Cloneable {
        public byte mType;
        public Path mPath;
        public float mRadius;
        public int mColor;
        public int noPoints = 0;

        @Override
        public String toString() {
            return "stroke(" + mType + ", path(" + (mPath) + "), " + mRadius + " , "
                    + Integer.toHexString(mColor) + ")";
        }

        @Override
        public StrokeData clone() throws CloneNotSupportedException {
            return (StrokeData) super.clone();
        }
    }

    public String getValueString() {

        switch (mParamMode) {
            case PARAM_SIZE:
            case PARAM_HUE:
            case PARAM_BRIGHTNESS:
            case PARAM_SATURATION:
            case PARAM_OPACITY:
                int val = ((BasicParameterInt) mAllParam[mParamMode]).getValue();
                return ((val > 0) ? " +" : " ") + val;
            case PARAM_STYLE:
                return "";

        }
        return "";
    }

    private Vector<StrokeData> mDrawing = new Vector<StrokeData>();
    private StrokeData mCurrent; // used in the currently drawing style

    public FilterDrawRepresentation() {
        super("Draw");
        setFilterClass(ImageFilterDraw.class);
        setSerializationName("DRAW");
        setFilterType(FilterRepresentation.TYPE_VIGNETTE);
        setTextId(R.string.imageDraw);
        setEditorId(EditorDraw.ID);
        setOverlayId(R.drawable.filtershow_drawing);
        setOverlayOnly(true);
    }

    @Override
    public String toString() {
        return getName() + " : strokes=" + mDrawing.size()
                + ((mCurrent == null) ? " no current "
                : ("draw=" + mCurrent.mType + " " + mCurrent.noPoints));
    }

    public Vector<StrokeData> getDrawing() {
        return mDrawing;
    }

    public StrokeData getCurrentDrawing() {
        return mCurrent;
    }

    @Override
    public FilterRepresentation copy() {
        FilterDrawRepresentation representation = new FilterDrawRepresentation();
        copyAllParameters(representation);
        return representation;
    }

    @Override
    protected void copyAllParameters(FilterRepresentation representation) {
        super.copyAllParameters(representation);
        representation.useParametersFrom(this);
    }

    @Override
    public boolean isNil() {
        return getDrawing().isEmpty();
    }

    @Override
    public void useParametersFrom(FilterRepresentation a) {
        if (a instanceof FilterDrawRepresentation) {
            FilterDrawRepresentation representation = (FilterDrawRepresentation) a;
            try {
                if (representation.mCurrent != null) {
                    mCurrent = (StrokeData) representation.mCurrent.clone();
                } else {
                    mCurrent = null;
                }
                if (representation.mDrawing != null) {
                    mDrawing = (Vector<StrokeData>) representation.mDrawing.clone();
                } else {
                    mDrawing = null;
                }

            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
        } else {
            Log.v(LOGTAG, "cannot use parameters from " + a);
        }
    }

    @Override
    public boolean equals(FilterRepresentation representation) {
        if (!super.equals(representation)) {
            return false;
        }
        if (representation instanceof FilterDrawRepresentation) {
            FilterDrawRepresentation fdRep = (FilterDrawRepresentation) representation;
            if (fdRep.mDrawing.size() != mDrawing.size())
                return false;
            if (fdRep.mCurrent == null && (mCurrent == null || mCurrent.mPath == null)) {
                return true;
            }
            if (fdRep.mCurrent != null && mCurrent != null && mCurrent.mPath != null) {
                if (fdRep.mCurrent.noPoints == mCurrent.noPoints) {
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private int computeCurrentColor(){
        float hue = 360 * mParamHue.getValue() / (float) mParamHue.getMaximum();
        float sat = mParamSaturation.getValue() / (float) mParamSaturation.getMaximum();
        float val = mParamBrightness.getValue() / (float) mParamBrightness.getMaximum();
        int op = mParamOpacity.getValue();
        float[] hsv = new float[]{hue, sat, val};
        return Color.HSVToColor(op, hsv);
    }

    public void startNewSection(float x, float y) {
        byte type = (byte) mParamStyle.getSelected();
        int color = computeCurrentColor();
        float size = mParamSize.getValue();
        mCurrent = new StrokeData();
        mCurrent.mColor = color;
        mCurrent.mRadius = size;
        mCurrent.mType = type;
        mCurrent.mPath = new Path();
        mCurrent.mPath.moveTo(x, y);
        mCurrent.noPoints = 0;
    }

    public void addPoint(float x, float y) {
        mCurrent.noPoints++;
        mCurrent.mPath.lineTo(x, y);
    }

    public void endSection(float x, float y) {
        mCurrent.mPath.lineTo(x, y);
        mCurrent.noPoints++;
        mDrawing.add(mCurrent);
        mCurrent = null;
    }

    public void clearCurrentSection() {
        mCurrent = null;
    }

    public void clear() {
        mCurrent = null;
        mDrawing.clear();
    }

}
