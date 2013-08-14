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

package com.android.gallery3d.filtershow.info;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.gallery3d.R;
import com.android.gallery3d.exif.ExifInterface;
import com.android.gallery3d.exif.ExifTag;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.imageshow.MasterImage;

import java.util.List;

public class InfoPanel extends Fragment {
    public static final String FRAGMENT_TAG = "InfoPanel";
    private static final String LOGTAG = FRAGMENT_TAG;
    private LinearLayout mMainView;
    private ImageView mImageThumbnail;
    private TextView mImageName;
    private TextView mImageSize;
    private TextView mExifData;
    private ImageButton mHideButton;

    private String createStringFromIfFound(ExifTag exifTag, int tag, int str) {
        String exifString = "";
        short tagId = exifTag.getTagId();
        if (tagId == ExifInterface.getTrueTagKey(tag)) {
            String label = getActivity().getString(str);
            exifString += "<b>" + label + ": </b>";
            exifString += exifTag.forceGetValueAsString();
            exifString += "<br>";
        }
        return exifString;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mMainView = (LinearLayout) inflater.inflate(
                R.layout.filtershow_info_panel, null, false);

        mImageThumbnail = (ImageView) mMainView.findViewById(R.id.imageThumbnail);
        Bitmap bitmap = MasterImage.getImage().getFilteredImage();
        mImageThumbnail.setImageBitmap(bitmap);

        mImageName = (TextView) mMainView.findViewById(R.id.imageName);
        mImageSize = (TextView) mMainView.findViewById(R.id.imageSize);
        mExifData = (TextView) mMainView.findViewById(R.id.exifData);
        mHideButton =(ImageButton) mMainView.findViewById(R.id.cancelInfo);

        HistogramView histogramView = (HistogramView) mMainView.findViewById(R.id.histogramView);

        histogramView.setBitmap(bitmap);

        mHideButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FilterShowActivity activity = (FilterShowActivity)getActivity();
                activity.toggleInformationPanel();
            }
        });
        Uri uri = MasterImage.getImage().getUri();
        String path = ImageLoader.getLocalPathFromUri(getActivity(), uri);
        Uri localUri = null;
        if (path != null) {
            localUri = Uri.parse(path);
        }

        if (localUri != null) {
            mImageName.setText(localUri.getLastPathSegment());
        }
        Rect originalBounds = MasterImage.getImage().getOriginalBounds();
        mImageSize.setText("" + originalBounds.width() + " x " + originalBounds.height());

        List<ExifTag> exif = MasterImage.getImage().getEXIF();
        String exifString = "";
        if (exif != null) {
            for (ExifTag tag : exif) {
                exifString += createStringFromIfFound(tag,
                        ExifInterface.TAG_MODEL,
                        R.string.filtershow_exif_model);
                exifString += createStringFromIfFound(tag,
                        ExifInterface.TAG_APERTURE_VALUE,
                        R.string.filtershow_exif_aperture);
                exifString += createStringFromIfFound(tag,
                        ExifInterface.TAG_FOCAL_LENGTH,
                        R.string.filtershow_exif_focal_length);
                exifString += createStringFromIfFound(tag,
                        ExifInterface.TAG_ISO_SPEED_RATINGS,
                        R.string.filtershow_exif_iso);
                exifString += createStringFromIfFound(tag,
                        ExifInterface.TAG_SUBJECT_DISTANCE,
                        R.string.filtershow_exif_subject_distance);
                exifString += createStringFromIfFound(tag,
                        ExifInterface.TAG_DATE_TIME_ORIGINAL,
                        R.string.filtershow_exif_date);
                exifString += createStringFromIfFound(tag,
                        ExifInterface.TAG_F_NUMBER,
                        R.string.filtershow_exif_f_stop);
                exifString += createStringFromIfFound(tag,
                        ExifInterface.TAG_EXPOSURE_TIME,
                        R.string.filtershow_exif_exposure_time);
                exifString += createStringFromIfFound(tag,
                        ExifInterface.TAG_COPYRIGHT,
                        R.string.filtershow_exif_copyright);
            }
        }
        mExifData.setText(Html.fromHtml(exifString));
        return mMainView;
    }
}
