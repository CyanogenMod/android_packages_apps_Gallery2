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

import android.content.Context;
import android.net.Uri;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.common.Utils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Arrays;
import java.util.HashMap;

public class ClusterAlbumSet extends MediaSet implements ContentListener {
    @SuppressWarnings("unused")
    private static final String TAG = "ClusterAlbumSet";
    private GalleryApp mApplication;
    private MediaSet mBaseSet;
    private int mKind;
    private ArrayList<ClusterAlbum> mAlbums = new ArrayList<ClusterAlbum>();
    private boolean mFirstReloadDone;

    private int mTotalMediaItemCount;
    /** mTotalSelectableMediaItemCount is the count of items
     * exclude not selectable such as Title item in TimeLine. */
    private int mTotalSelectableMediaItemCount;
    private ArrayList<Integer> mAlbumItemCountList;

    public ClusterAlbumSet(Path path, GalleryApp application,
            MediaSet baseSet, int kind) {
        super(path, INVALID_DATA_VERSION);
        mApplication = application;
        mBaseSet = baseSet;
        mKind = kind;
        baseSet.addContentListener(this);
    }

    @Override
    public MediaSet getSubMediaSet(int index) {
        return mAlbums.get(index);
    }

    @Override
    public int getSubMediaSetCount() {
        return mAlbums.size();
    }

    @Override
    public String getName() {
        return mBaseSet.getName();
    }

    @Override
    public long reload() {
        synchronized(this){
            if (mBaseSet.reload() > mDataVersion) {
                if (mFirstReloadDone) {
                    updateClustersContents();
                } else {
                    updateClusters();
                    mFirstReloadDone = true;
                }
                mDataVersion = nextVersionNumber();
            }
            if (mKind == ClusterSource.CLUSTER_ALBUMSET_TIME) {
                calculateTotalItemsCount();
                calculateTotalSelectableItemsCount();
            }
        }
        return mDataVersion;
    }

    @Override
    public void onContentDirty() {
        notifyContentChanged();
    }

    private void updateClusters() {
        mAlbums.clear();
        Clustering clustering;
        Context context = mApplication.getAndroidContext();
        switch (mKind) {
            case ClusterSource.CLUSTER_ALBUMSET_TIME:
                clustering = new TimeClustering(context);
                break;
            case ClusterSource.CLUSTER_ALBUMSET_LOCATION:
                clustering = new LocationClustering(context);
                break;
            case ClusterSource.CLUSTER_ALBUMSET_TAG:
                clustering = new TagClustering(context);
                break;
            case ClusterSource.CLUSTER_ALBUMSET_FACE:
                clustering = new FaceClustering(context);
                break;
            default: /* CLUSTER_ALBUMSET_SIZE */
                clustering = new SizeClustering(context);
                break;
        }

        clustering.run(mBaseSet);
        int n = clustering.getNumberOfClusters();
        DataManager dataManager = mApplication.getDataManager();
        for (int i = 0; i < n; i++) {
            Path childPath;
            String childName = clustering.getClusterName(i);
            if (mKind == ClusterSource.CLUSTER_ALBUMSET_TAG) {
                childPath = mPath.getChild(Uri.encode(childName));
            } else if (mKind == ClusterSource.CLUSTER_ALBUMSET_SIZE) {
                long minSize = ((SizeClustering) clustering).getMinSize(i);
                childPath = mPath.getChild(minSize);
            } else {
                childPath = mPath.getChild(i);
            }

            ClusterAlbum album;
            synchronized (DataManager.LOCK) {
                album = (ClusterAlbum) dataManager.peekMediaObject(childPath);
                if (album == null) {
                    album = new ClusterAlbum(childPath, dataManager, this, mKind);
                }
            }
            album.setMediaItems(clustering.getCluster(i));
            album.setName(childName);
            album.setCoverMediaItem(clustering.getClusterCover(i));
            album.setImageItemCount(clustering.getClusterImageCount(i));
            album.setVideoItemCount(clustering.getClusterVideoCount(i));
            mAlbums.add(album);
        }
    }

    protected void updateClustersContents() {
        final HashMap<Path, Integer> existing = new HashMap<Path, Integer>();
        mBaseSet.enumerateTotalMediaItems(new MediaSet.ItemConsumer() {
            @Override
            public void consume(int index, MediaItem item) {
                existing.put(item.getPath(), item.getMediaType());
            }
        });

        int n = mAlbums.size();

        // The loop goes backwards because we may remove empty albums from
        // mAlbums.
        for (int i = n - 1; i >= 0; i--) {
            ArrayList<Path> oldPaths = mAlbums.get(i).getMediaItems();
            ArrayList<Path> newPaths = new ArrayList<Path>();
            int m = oldPaths.size();
            int imageCount = 0;
            int videoCount = 0;
            int mediaType = MEDIA_TYPE_UNKNOWN;
            ClusterAlbum album = mAlbums.get(i);
            for (int j = 0; j < m; j++) {
                Path p = oldPaths.get(j);
                if (existing.containsKey(p)) {
                    newPaths.add(p);
                    mediaType = existing.get(p);
                    existing.remove(p);
                    if(mediaType == MediaObject.MEDIA_TYPE_IMAGE) {
                        imageCount++;
                    } else if(mediaType == MediaObject.MEDIA_TYPE_VIDEO) {
                        videoCount++;
                    }
                }
            }
            album.setImageItemCount(imageCount);
            album.setVideoItemCount(videoCount);
            album.setMediaItems(newPaths);
            if (newPaths.isEmpty()) {
                mAlbums.remove(i);
            }
        }

        updateClusters();
    }

    private void calculateTotalSelectableItemsCount() {
        mTotalSelectableMediaItemCount = 0;
        if (mAlbums != null && mAlbums.size() > 0) {
            for (ClusterAlbum album : mAlbums) {
                int count = album.getSelectableItemCount();
                mTotalSelectableMediaItemCount += count;
            }
        }
    }

    @Override
    public int getSelectableItemCount() {
        return mTotalSelectableMediaItemCount;
    }

  private void calculateTotalItemsCount() {
      mTotalMediaItemCount = 0;
      if( mAlbums != null && mAlbums.size() > 0) {
          mAlbumItemCountList = new ArrayList<Integer>();
          for(ClusterAlbum album: mAlbums) {
              int count = album.getMediaItemCount();
              mTotalMediaItemCount = mTotalMediaItemCount + count;
              mAlbumItemCountList.add(mTotalMediaItemCount);
          }
      }
  }

  @Override
  public int getMediaItemCount() {
      return mTotalMediaItemCount;
  }

  @Override
  public ArrayList<MediaItem> getMediaItem(int start, int count) {
      if ((start + count) > mTotalMediaItemCount ) {
          count  = mTotalMediaItemCount - start;
      }
      if (count <= 0) return null;
      ArrayList<MediaItem> mediaItems = new ArrayList<MediaItem>();
      int startAlbum = findTimelineAlbumIndex(start);
      int endAlbum = findTimelineAlbumIndex(start + count - 1);
      int s;
      int lCount;
      if (mAlbums.size() > 0 && mAlbumItemCountList.size() > 0) {
          s = mAlbums.get(startAlbum).getTotalMediaItemCount() - 
                  (mAlbumItemCountList.get(startAlbum) - start);
          for (int i = startAlbum; i <= endAlbum && i < mAlbums.size(); ++i) {
              int albumCount = mAlbums.get(i).getTotalMediaItemCount();
              lCount = Math.min(albumCount - s, count);
              ArrayList<MediaItem> items = mAlbums.get(i).getMediaItem(s, lCount);
              if (items != null)
                  mediaItems.addAll(items);
              count -= lCount;
              s = 0;
          }
      }
      return mediaItems;
  }

  public int findTimelineAlbumIndex(int itemIndex) {
      int index = Arrays.binarySearch(mAlbumItemCountList.toArray(new Integer[0]), itemIndex);
      if (index <  mTotalMediaItemCount && index >=  0)
          return index + 1;
      if (index < 0) {
          index = (index * (-1)) - 1;
      }
      return index;
  }

  public ClusterAlbum getAlbumFromindex(int index) {
      int aIndex = findTimelineAlbumIndex(index);
      if (aIndex < mAlbums.size() && aIndex >= 0) {
          return mAlbums.get(aIndex);
      }
      return null;
  }
}
