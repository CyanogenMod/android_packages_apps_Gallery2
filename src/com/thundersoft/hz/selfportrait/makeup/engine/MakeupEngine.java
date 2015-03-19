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

package com.thundersoft.hz.selfportrait.makeup.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

public class MakeupEngine {
    static {
        try {
            System.loadLibrary("ts_face_beautify_jni");
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
            Log.e(MakeupEngine.class.getName(), "ts_face_beautify_jni library not found!");
        }
    }

    private static MakeupEngine mInstance;

    private MakeupEngine() {

    }

    public static MakeupEngine getMakeupObj() {
        if(mInstance == null) {
            mInstance = new MakeupEngine();
        }

        return mInstance;
    }

    private Context mContext;

    public void setContext(Context context) {
        mContext = context;
    }

    public Context getContext() {
        return mContext;
    }

    /**
     * FUNCTION: doProcessBeautify
     * Do process face region clean and whiten.
     * @param inBitmap, the Bitmap instance which have face region, MUST not null.
     * @param outBitmap, the result of process, MUST not null.
     * @param frameWidth,frameHeight, the size of inBitmap.
     * @param faceRect, the face region in inBitmap.
     * @param cleanLevel, the level of clean.(0-100)
     * @param whiteLevel, the level of white.(0-100)
     */
    public static native boolean doProcessBeautify(Bitmap inBitmap, Bitmap outBitmap, int frameWidth, int frameHeight,
            Rect faceRect, int cleanLevel, int beautyLevel);

    /**
     * FUNCTION: doWarpFace
     * Do process face region warp and big eye.
     * @param inBitmap, the Bitmap instance which have face region, MUST not null.
     * @param outBitmap, the result of process, MUST not null.
     * @param frameWidth, the size of inBitmap.
     * @param frameHeight, the size of inBitmap.
     * @param leftEye, the left eye rectangle
     * @param rightEye, the right eye rectangle
     * @param mouth, the mouth rectangle
     * @param bigEyeLevel, the level of big eye.(0-100)
     * @param trimFaceLevel, the level of trim face.(0-100)
     */
    public static native boolean doWarpFace(Bitmap inBitmap, Bitmap outBitmap, int frameWidth, int frameHeight,
            Rect leftEye, Rect rightEye, Rect mouth, int bigEyeLevel, int trimFaceLevel);
}
