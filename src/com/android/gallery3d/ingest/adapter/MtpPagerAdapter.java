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

package com.android.gallery3d.ingest.adapter;

import com.android.gallery3d.R;
import com.android.gallery3d.ingest.data.IngestObjectInfo;
import com.android.gallery3d.ingest.data.MtpDeviceIndex;
import com.android.gallery3d.ingest.data.MtpDeviceIndex.SortOrder;
import com.android.gallery3d.ingest.ui.MtpFullscreenView;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.v4.view.PagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Adapter for full-screen MTP pager.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
public class MtpPagerAdapter extends PagerAdapter {

  private LayoutInflater mInflater;
  private int mGeneration = 0;
  private CheckBroker mBroker;
  private MtpDeviceIndex mModel;
  private SortOrder mSortOrder = SortOrder.DESCENDING;

  private MtpFullscreenView mReusableView = null;

  public MtpPagerAdapter(Context context, CheckBroker broker) {
    super();
    mInflater = LayoutInflater.from(context);
    mBroker = broker;
  }

  public void setMtpDeviceIndex(MtpDeviceIndex index) {
    mModel = index;
    notifyDataSetChanged();
  }

  @Override
  public int getCount() {
    return mModel != null ? mModel.sizeWithoutLabels() : 0;
  }

  @Override
  public void notifyDataSetChanged() {
    mGeneration++;
    super.notifyDataSetChanged();
  }

  public int translatePositionWithLabels(int position) {
    if (mModel == null) {
      return -1;
    }
    return mModel.getPositionWithoutLabelsFromPosition(position, mSortOrder);
  }

  @Override
  public void finishUpdate(ViewGroup container) {
    mReusableView = null;
    super.finishUpdate(container);
  }

  @Override
  public boolean isViewFromObject(View view, Object object) {
    return view == object;
  }

  @Override
  public void destroyItem(ViewGroup container, int position, Object object) {
    MtpFullscreenView v = (MtpFullscreenView) object;
    container.removeView(v);
    mBroker.unregisterOnCheckedChangeListener(v);
    mReusableView = v;
  }

  @Override
  public Object instantiateItem(ViewGroup container, int position) {
    MtpFullscreenView v;
    if (mReusableView != null) {
      v = mReusableView;
      mReusableView = null;
    } else {
      v = (MtpFullscreenView) mInflater.inflate(R.layout.ingest_fullsize, container, false);
    }
    IngestObjectInfo i = mModel.getWithoutLabels(position, mSortOrder);
    v.getImageView().setMtpDeviceAndObjectInfo(mModel.getDevice(), i, mGeneration);
    v.setPositionAndBroker(position, mBroker);
    container.addView(v);
    return v;
  }
}
