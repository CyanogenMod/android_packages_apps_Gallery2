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

package com.android.gallery3d.filtershow.controller;

public class ParameterColor implements Parameter {
    public static String sParameterType = "ParameterColor";
    protected Control mControl;
    protected FilterView mEditor;
    float[] mHSVO = new float[4];
    String mParameterName;
    int mValue;
    public final int  ID;

    public ParameterColor(int id) {
        ID = id;
    }

    @Override
    public String getParameterType() {
        return sParameterType;
    }

    public void setColor(float[] hsvo) {
        mHSVO = hsvo;
    }

    public float[] getColor() {
        mHSVO[3] = getValue() ;
        return mHSVO;
    }


    public void copyFrom(Parameter src) {
        if (!(src instanceof BasicParameterInt)) {
            throw new IllegalArgumentException(src.getClass().getName());
        }
        BasicParameterInt p = (BasicParameterInt) src;

        mValue = p.mValue;
    }


    @Override
    public String getParameterName() {
        return mParameterName;
    }

    @Override
    public String getValueString() {
        return  "("+Integer.toHexString(mValue)+")";
    }

    @Override
    public void setController(Control control) {
        mControl = control;
    }

    public int getValue() {
        return mValue;
    }

    public void setValue(int value) {
        mValue = value;
    }

    @Override
    public String toString() {
        return getValueString();
    }

    @Override
    public void setFilterView(FilterView editor) {
        mEditor = editor;
    }
}
