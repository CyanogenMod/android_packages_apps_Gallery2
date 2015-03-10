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

package com.thundersoft.hz.selfportrait.detect;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

public class FaceDetect {
    private static final String TAG = "FaceDetect";

    private int mHandle = 0;

    static {
        try {
            System.loadLibrary("ts_detected_face_jni");
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
            Log.e(TAG, "ts_detected_face_jni library not found!");
        }
    }

    /**
     * initialize method,MUST called at first time.
     */
    public void initialize() {
        mHandle = native_create();
    }

    /**
     * uninitialize method,MUST called at last time.
     */

    public void uninitialize() {
        native_destroy(mHandle);
    }

    /**
     * dectectFeatures method,MUST called after initialize method and before
     * uninitialize method.
     *
     * @param bmp, Android Bitmap instance,MUST not null.
     * @return FaceInfo array if success, otherwise return null.
     */
    public FaceInfo[] dectectFeatures(Bitmap bmp) {
        int count = native_detect(mHandle, bmp);
        if (count < 1) {
            return null;
        }
        FaceInfo[] res = new FaceInfo[count];
        for (int i = 0; i < count; i++) {
            FaceInfo face = new FaceInfo();
            native_face_info(mHandle, i, face.face, face.eye1, face.eye2, face.mouth);
            res[i] = face;
        }
        return res;
    }

    private static native int native_create();
    private static native void native_destroy(int handle);
    private static native int native_detect(int handle, Bitmap bmp);
    private static native int native_face_info(int handle, int index, Rect face, Rect eye1,
            Rect eye2, Rect mouth);
}
