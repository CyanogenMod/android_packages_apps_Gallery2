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

package com.android.gallery3d.data;

import android.content.ContentResolver;
import android.drm.DrmHelper;
import android.net.Uri;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.android.gallery3d.app.GalleryApp;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

class UriSource extends MediaSource {
    @SuppressWarnings("unused")
    private static final String TAG = "UriSource";
    private static final String IMAGE_TYPE_PREFIX = "image/";
    private static final String IMAGE_TYPE_ANY = "image/*";
    private static final String CHARSET_UTF_8 = "utf-8";

    private GalleryApp mApplication;

    public UriSource(GalleryApp context) {
        super("uri");
        mApplication = context;
    }

    @Override
    public MediaObject createMediaObject(Path path) {
        String segment[] = path.split();
        if (segment.length != 3) {
            throw new RuntimeException("bad path: " + path);
        }
        try {
            String uri = URLDecoder.decode(segment[1], CHARSET_UTF_8);
            String type = URLDecoder.decode(segment[2], CHARSET_UTF_8);
            return new UriImage(mApplication, path, Uri.parse(uri), type);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    private String getMimeType(Uri uri) {
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            String extension =
                    MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            String type = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension.toLowerCase());
            if (type != null) return type;
        }
        // Assume the type is image if the type cannot be resolved
        // This could happen for "http" URI.
        String type = mApplication.getContentResolver().getType(uri);
        if (type == null) type = "image/*";
        return type;
    }

    @Override
    public Path findPathByUri(Uri uri, String type) {
        String mimeType = getMimeType(uri);
        if (DrmHelper.isDrmMimeType(mimeType)) {
            String path = DrmHelper.getFilePath(
                    mApplication.getAndroidContext(), uri);
            if (!TextUtils.isEmpty(path)) {
                try {
                    return Path.fromString("/uri/"
                            + URLEncoder.encode(path, CHARSET_UTF_8) + "/"
                            + URLEncoder.encode(type, CHARSET_UTF_8));
                } catch (UnsupportedEncodingException e) {
                    throw new AssertionError(e);
                }
            }
        }

        // Try to find a most specific type but it has to be started with "image/"
        if ((type == null) || (IMAGE_TYPE_ANY.equals(type)
                && mimeType.startsWith(IMAGE_TYPE_PREFIX))) {
            type = mimeType;
        }

        if (type.startsWith(IMAGE_TYPE_PREFIX)) {
            try {
                return Path.fromString("/uri/"
                        + URLEncoder.encode(uri.toString(), CHARSET_UTF_8)
                        + "/" +URLEncoder.encode(type, CHARSET_UTF_8));
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError(e);
            }
        }
        // We have no clues that it is an image
        return null;
    }
}
