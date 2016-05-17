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
package com.android.gallery3d.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

public class IntentHelper {

    public static Intent getCameraIntent(Context context) {
        Intent intentCamera2 = new Intent(Intent.ACTION_MAIN)
                .setClassName("com.android.camera2", "com.android.camera.CameraLauncher");
        Intent intentSnap = new Intent(Intent.ACTION_MAIN)
                .setClassName("org.codeaurora.snapcam", "com.android.camera.CameraLauncher");
        if (isIntentAvailable(context, intentCamera2)) {
            return intentCamera2;
        } else if (isIntentAvailable(context, intentCamera2)) {
            return intentSnap;
        }

        return null;
    }

    public static Intent getGalleryIntent(Context context) {
        return new Intent(Intent.ACTION_MAIN)
            .setClassName("com.android.gallery3d", "com.android.gallery3d.app.GalleryActivity");
    }

    public static boolean isIntentAvailable(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return !list.isEmpty();
    }
}
