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

import java.util.ArrayList;
import com.android.gallery3d.util.GalleryUtils;

public class ClusterAlbum extends MediaSet implements ContentListener {
    @SuppressWarnings("unused")
    private static final String TAG = "ClusterAlbum";
    private ArrayList<Path> mPaths = new ArrayList<Path>();
    private String mName = "";
    private DataManager mDataManager;
    private MediaSet mClusterAlbumSet;
    private MediaItem mCover;
    private final int INVALID_COUNT = -1;
    private int mImageCount = INVALID_COUNT;
    private int mVideoCount = INVALID_COUNT;
    private int mKind = -1;


    private TimeLineTitleMediaItem mTimelineTitleMediaItem;

    public ClusterAlbum(Path path, DataManager dataManager,
            MediaSet clusterAlbumSet, int kind) {
        super(path, nextVersionNumber());
        mDataManager = dataManager;
        mClusterAlbumSet = clusterAlbumSet;
        mClusterAlbumSet.addContentListener(this);
        mKind = kind;
        mTimelineTitleMediaItem = new TimeLineTitleMediaItem(path);
    }

    public void setCoverMediaItem(MediaItem cover) {
        mCover = cover;
    }

    @Override
    public MediaItem getCoverMediaItem() {
        return mCover != null ? mCover : super.getCoverMediaItem();
    }

    void setMediaItems(ArrayList<Path> paths) {
        mPaths = paths;
    }

    public ArrayList<Path> getMediaItems() {
        return mPaths;
    }

    public void setName(String name) {
        mName = name;
        mTimelineTitleMediaItem.setTitle(name);
        /*if (mKind == ClusterSource.CLUSTER_ALBUMSET_TIME) {
            mTimelineTitleMediaItem = new TimeLineTitleMediaItem(name);
        }*/
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public int getMediaItemCount() {
        if (mKind == ClusterSource.CLUSTER_ALBUMSET_TIME) {
            return mPaths.size()+1;
        }
        return mPaths.size();
    }

    @Override
    public int getSelectableItemCount() {
        return mPaths.size();
    }

    public void setImageItemCount(int count) {
        mImageCount = count;
        if (mTimelineTitleMediaItem != null && mKind == ClusterSource.CLUSTER_ALBUMSET_TIME) {
            mTimelineTitleMediaItem.setImageCount(count);
        }
    }

    public void setVideoItemCount(int count) {
        mVideoCount = count;
        if (mTimelineTitleMediaItem != null && mKind == ClusterSource.CLUSTER_ALBUMSET_TIME) {
            mTimelineTitleMediaItem.setVideoCount(count);
        }
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        //return getMediaItemFromPath(mPaths, start, count, mDataManager);
        if (mKind == ClusterSource.CLUSTER_ALBUMSET_TIME) {
            if (mPaths.size() <= 0) return null;
            if (start == 0) {
                ArrayList<MediaItem> mediaItemList = new ArrayList<MediaItem>();
                mediaItemList.addAll(getMediaItemFromPath(mPaths, start, count - 1, mDataManager));
                mediaItemList.add(0, mTimelineTitleMediaItem);
                return mediaItemList;
            } else {
                return getMediaItemFromPath(mPaths, start - 1, count, mDataManager);
            }
        } else {
            return getMediaItemFromPath(mPaths, start, count, mDataManager);
        }
    }

    @Override
    public int getImageItemCount() {
        return mImageCount;
    }

    @Override
    public int getVideoItemCount() {
        return mVideoCount;
    }

    public static ArrayList<MediaItem> getMediaItemFromPath(
            ArrayList<Path> paths, int start, int count,
            DataManager dataManager) {
        if (start >= paths.size()) {
            return new ArrayList<MediaItem>();
        }
        int end = Math.min(start + count, paths.size());
        ArrayList<Path> subset = new ArrayList<Path>(paths.subList(start, end));
        final MediaItem[] buf = new MediaItem[end - start];
        ItemConsumer consumer = new ItemConsumer() {
            @Override
            public void consume(int index, MediaItem item) {
                buf[index] = item;
            }
        };
        dataManager.mapMediaItems(subset, consumer, 0);
        ArrayList<MediaItem> result = new ArrayList<MediaItem>(end - start);
        for (int i = 0; i < buf.length; i++) {
            if(buf[i] != null) {
                result.add(buf[i]);
            }
        }
        return result;
    }

    @Override
    protected int enumerateMediaItems(ItemConsumer consumer, int startIndex) {
        mDataManager.mapMediaItems(mPaths, consumer, startIndex);
        return mPaths.size();
    }

    @Override
    public int getTotalMediaItemCount() {
        if (mKind == ClusterSource.CLUSTER_ALBUMSET_TIME) {
            return mPaths.size()+1;
        }
        return mPaths.size();
    }

    @Override
    public int getMediaType() {
        // return correct type of Timeline Title.
        if (mKind == ClusterSource.CLUSTER_ALBUMSET_TIME) {
            return MEDIA_TYPE_TIMELINE_TITLE;
        }
        return super.getMediaType();
    }

    @Override
    public long reload() {
        if (mClusterAlbumSet.reload() > mDataVersion) {
            mDataVersion = nextVersionNumber();
        }
        return mDataVersion;
    }

    @Override
    public void onContentDirty() {
        notifyContentChanged();
    }

    @Override
    public int getSupportedOperations() {
        // Timeline title item doesn't support anything, just its sub objects supported.
        if (mKind == ClusterSource.CLUSTER_ALBUMSET_TIME) {
            return 0;
        }
        return SUPPORT_SHARE | SUPPORT_DELETE | SUPPORT_INFO;
    }

    @Override
    public void delete() {
        if ((getSupportedOperations() & MediaObject.SUPPORT_DELETE) == 0) return;
        ItemConsumer consumer = new ItemConsumer() {
            @Override
            public void consume(int index, MediaItem item) {
                if ((item.getSupportedOperations() & SUPPORT_DELETE) != 0) {
                    item.delete();
                }
            }
        };
        mDataManager.mapMediaItems(mPaths, consumer, 0);
    }

    @Override
    public boolean isLeafAlbum() {
        return true;
    }

    public TimeLineTitleMediaItem getTimelineTitle() {
        return mTimelineTitleMediaItem;
    }

    public void setClusterKind(int kind) {
        if (mKind == kind) {
            return;
        }
        mKind = kind;
        refreshImageItemCount();
    }

    private void refreshImageItemCount() {
        setImageItemCount(mImageCount);
    }
}
