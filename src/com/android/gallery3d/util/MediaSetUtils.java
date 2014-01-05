/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.os.Environment;

import com.android.gallery3d.data.LocalAlbum;
import com.android.gallery3d.data.LocalMergeAlbum;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;

import java.util.Comparator;

public class MediaSetUtils {
    public static final Comparator<MediaSet> NAME_COMPARATOR = new NameComparator();

    private static String mRoot = Environment.getExternalStorageDirectory().toString();

    public static void setRoot(String root) {
        mRoot = root;
    }

    public static final int DOWNLOAD_BUCKET_ID = GalleryUtils.getBucketId(
            Environment.getExternalStorageDirectory().toString() + "/"
            + BucketNames.DOWNLOAD);
    public static final int EDITED_ONLINE_PHOTOS_BUCKET_ID = GalleryUtils.getBucketId(
            Environment.getExternalStorageDirectory().toString() + "/"
            + BucketNames.EDITED_ONLINE_PHOTOS);
    public static final int IMPORTED_BUCKET_ID = GalleryUtils.getBucketId(
            Environment.getExternalStorageDirectory().toString() + "/"
            + BucketNames.IMPORTED);
    public static final int SNAPSHOT_BUCKET_ID = GalleryUtils.getBucketId(
            Environment.getExternalStorageDirectory().toString() +
            "/" + BucketNames.SCREENSHOTS);

    public static int getCameraBucketId() {
        return GalleryUtils.getBucketId(mRoot + "/" + BucketNames.CAMERA);
    }

    public static boolean isCameraSource(Path path) {
        return path.equalsIgnoreCase("/local/all/" + getCameraBucketId())
                || path.equalsIgnoreCase("/local/image/" + getCameraBucketId())
                || path.equalsIgnoreCase("/local/video/" + getCameraBucketId());
    }

    // Sort MediaSets by name
    public static class NameComparator implements Comparator<MediaSet> {
        @Override
        public int compare(MediaSet set1, MediaSet set2) {
            int result = set1.getName().compareToIgnoreCase(set2.getName());
            if (result != 0) return result;
            return set1.getPath().toString().compareTo(set2.getPath().toString());
        }
    }
}
