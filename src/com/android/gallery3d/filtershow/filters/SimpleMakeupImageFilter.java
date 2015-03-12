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

package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;
import android.util.Log;

import com.thundersoft.hz.selfportrait.detect.FaceDetect;
import com.thundersoft.hz.selfportrait.detect.FaceInfo;

public abstract class SimpleMakeupImageFilter extends SimpleImageFilter {
    private static final String LOGTAG = "SimpleMakeupImageFilter";
    protected static final int MAKEUP_INTENSITY = 50;

    public static final boolean HAS_TS_MAKEUP = android.os.SystemProperties.getBoolean("persist.ts.postmakeup", false);

    public SimpleMakeupImageFilter() {
    }

    public FilterRepresentation getDefaultRepresentation() {
        FilterRepresentation representation = new FilterBasicRepresentation("Default", 0,
                MAKEUP_INTENSITY, 100);
        representation.setShowParameterValue(true);
        return representation;
    }

    protected FaceInfo detectFaceInfo(Bitmap bitmap) {
        FaceDetect faceDetect = new FaceDetect();
        faceDetect.initialize();
        FaceInfo[] faceInfos = faceDetect.dectectFeatures(bitmap);
        faceDetect.uninitialize();

        Log.v(LOGTAG, "SimpleMakeupImageFilter.detectFaceInfo(): detect faceNum is "
                + (faceInfos != null ? faceInfos.length : "NULL"));
        if (faceInfos == null || faceInfos.length <= 0) {
            return null;
        }

        return faceInfos[0];
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, int quality) {
        if (getParameters() == null) {
            return bitmap;
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if(w % 2 != 0 || h % 2 != 0) {
            return bitmap;
        }
        int value = getParameters().getValue();
        applyHelper(bitmap, w, h, value);
        return bitmap;
    }

    private void applyHelper(Bitmap bitmap, int w, int h, int value) {
        FaceInfo faceInfo = detectFaceInfo(bitmap);
        if(faceInfo != null) {
            doMakeupEffect(bitmap, faceInfo, w, h, value);
        }
    }

    abstract void doMakeupEffect(Bitmap bitmap, FaceInfo faceInfo, int width, int height,
            int value);

}
