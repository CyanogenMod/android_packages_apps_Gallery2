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

package com.android.gallery3d.ingest.data;

import android.annotation.TargetApi;
import android.content.Context;
import android.mtp.MtpDevice;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.util.Log;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Task that handles the copying of items from an MTP device.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
public class ImportTask implements Runnable {

  private static final String TAG = "ImportTask";

  /**
   * Import progress listener.
   */
  public interface Listener {
    void onImportProgress(int visitedCount, int totalCount, String pathIfSuccessful);

    void onImportFinish(Collection<IngestObjectInfo> objectsNotImported, int visitedCount);
  }

  private static final String WAKELOCK_LABEL = "Google Photos MTP Import Task";

  private Listener mListener;
  private String mDestAlbumName;
  private Collection<IngestObjectInfo> mObjectsToImport;
  private MtpDevice mDevice;
  private PowerManager.WakeLock mWakeLock;

  public ImportTask(MtpDevice device, Collection<IngestObjectInfo> objectsToImport,
      String destAlbumName, Context context) {
    mDestAlbumName = destAlbumName;
    mObjectsToImport = objectsToImport;
    mDevice = device;
    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, WAKELOCK_LABEL);
  }

  public void setListener(Listener listener) {
    mListener = listener;
  }

  @Override
  public void run() {
    mWakeLock.acquire();
    try {
      List<IngestObjectInfo> objectsNotImported = new LinkedList<IngestObjectInfo>();
      int visited = 0;
      int total = mObjectsToImport.size();
      mListener.onImportProgress(visited, total, null);
      File dest = new File(Environment.getExternalStorageDirectory(), mDestAlbumName);
      dest.mkdirs();
      for (IngestObjectInfo object : mObjectsToImport) {
        visited++;
        String importedPath = null;
        if (hasSpaceForSize(object.getCompressedSize())) {
          importedPath = new File(dest, object.getName(mDevice)).getAbsolutePath();
          if (!mDevice.importFile(object.getObjectHandle(), importedPath)) {
            importedPath = null;
          }
        }
        if (importedPath == null) {
          objectsNotImported.add(object);
        }
        if (mListener != null) {
          mListener.onImportProgress(visited, total, importedPath);
        }
      }
      if (mListener != null) {
        mListener.onImportFinish(objectsNotImported, visited);
      }
    } finally {
      mListener = null;
      mWakeLock.release();
    }
  }

  private static boolean hasSpaceForSize(long size) {
    String state = Environment.getExternalStorageState();
    if (!Environment.MEDIA_MOUNTED.equals(state)) {
      return false;
    }

    String path = Environment.getExternalStorageDirectory().getPath();
    try {
      StatFs stat = new StatFs(path);
      return stat.getAvailableBlocks() * (long) stat.getBlockSize() > size;
    } catch (Exception e) {
      Log.i(TAG, "Fail to access external storage", e);
    }
    return false;
  }
}
