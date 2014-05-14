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

package com.android.gallery3d.ingest;

import com.android.gallery3d.R;
import com.android.gallery3d.ingest.data.ImportTask;
import com.android.gallery3d.ingest.data.IngestObjectInfo;
import com.android.gallery3d.ingest.data.MtpClient;
import com.android.gallery3d.ingest.data.MtpDeviceIndex;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.mtp.MtpDevice;
import android.mtp.MtpDeviceInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.SparseBooleanArray;
import android.widget.Adapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Service for MTP importing tasks.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
public class IngestService extends Service implements ImportTask.Listener,
    MtpDeviceIndex.ProgressListener, MtpClient.Listener {

  /**
   * Convenience class to allow easy access to the service instance.
   */
  public class LocalBinder extends Binder {
    IngestService getService() {
      return IngestService.this;
    }
  }

  private static final int PROGRESS_UPDATE_INTERVAL_MS = 180;

  private MtpClient mClient;
  private final IBinder mBinder = new LocalBinder();
  private ScannerClient mScannerClient;
  private MtpDevice mDevice;
  private String mDevicePrettyName;
  private MtpDeviceIndex mIndex;
  private IngestActivity mClientActivity;
  private boolean mRedeliverImportFinish = false;
  private int mRedeliverImportFinishCount = 0;
  private Collection<IngestObjectInfo> mRedeliverObjectsNotImported;
  private boolean mRedeliverNotifyIndexChanged = false;
  private boolean mRedeliverIndexFinish = false;
  private NotificationManager mNotificationManager;
  private NotificationCompat.Builder mNotificationBuilder;
  private long mLastProgressIndexTime = 0;
  private boolean mNeedRelaunchNotification = false;

  @Override
  public void onCreate() {
    super.onCreate();
    mScannerClient = new ScannerClient(this);
    mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    mNotificationBuilder = new NotificationCompat.Builder(this);
    // TODO(georgescu): Use a better drawable for the notificaton?
    mNotificationBuilder.setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentIntent(PendingIntent.getActivity(this, 0,
            new Intent(this, IngestActivity.class), 0));
    mIndex = MtpDeviceIndex.getInstance();
    mIndex.setProgressListener(this);

    mClient = new MtpClient(getApplicationContext());
    List<MtpDevice> devices = mClient.getDeviceList();
    if (!devices.isEmpty()) {
      setDevice(devices.get(0));
    }
    mClient.addListener(this);
  }

  @Override
  public void onDestroy() {
    mClient.close();
    mIndex.unsetProgressListener(this);
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }

  private void setDevice(MtpDevice device) {
    if (mDevice == device) {
      return;
    }
    mRedeliverImportFinish = false;
    mRedeliverObjectsNotImported = null;
    mRedeliverNotifyIndexChanged = false;
    mRedeliverIndexFinish = false;
    mDevice = device;
    mIndex.setDevice(mDevice);
    if (mDevice != null) {
      MtpDeviceInfo deviceInfo = mDevice.getDeviceInfo();
      if (deviceInfo == null) {
        setDevice(null);
        return;
      } else {
        mDevicePrettyName = deviceInfo.getModel();
        mNotificationBuilder.setContentTitle(mDevicePrettyName);
        new Thread(mIndex.getIndexRunnable()).start();
      }
    } else {
      mDevicePrettyName = null;
    }
    if (mClientActivity != null) {
      mClientActivity.notifyIndexChanged();
    } else {
      mRedeliverNotifyIndexChanged = true;
    }
  }

  protected MtpDeviceIndex getIndex() {
    return mIndex;
  }

  protected void setClientActivity(IngestActivity activity) {
    if (mClientActivity == activity) {
      return;
    }
    mClientActivity = activity;
    if (mClientActivity == null) {
      if (mNeedRelaunchNotification) {
        mNotificationBuilder.setProgress(0, 0, false)
            .setContentText(getResources().getText(R.string.ingest_scanning_done));
        mNotificationManager.notify(R.id.ingest_notification_scanning,
            mNotificationBuilder.build());
      }
      return;
    }
    mNotificationManager.cancel(R.id.ingest_notification_importing);
    mNotificationManager.cancel(R.id.ingest_notification_scanning);
    if (mRedeliverImportFinish) {
      mClientActivity.onImportFinish(mRedeliverObjectsNotImported,
          mRedeliverImportFinishCount);
      mRedeliverImportFinish = false;
      mRedeliverObjectsNotImported = null;
    }
    if (mRedeliverNotifyIndexChanged) {
      mClientActivity.notifyIndexChanged();
      mRedeliverNotifyIndexChanged = false;
    }
    if (mRedeliverIndexFinish) {
      mClientActivity.onIndexingFinished();
      mRedeliverIndexFinish = false;
    }
    if (mDevice != null) {
      mNeedRelaunchNotification = true;
    }
  }

  protected void importSelectedItems(SparseBooleanArray selected, Adapter adapter) {
    List<IngestObjectInfo> importHandles = new ArrayList<IngestObjectInfo>();
    for (int i = 0; i < selected.size(); i++) {
      if (selected.valueAt(i)) {
        Object item = adapter.getItem(selected.keyAt(i));
        if (item instanceof IngestObjectInfo) {
          importHandles.add(((IngestObjectInfo) item));
        }
      }
    }
    ImportTask task = new ImportTask(mDevice, importHandles, mDevicePrettyName, this);
    task.setListener(this);
    mNotificationBuilder.setProgress(0, 0, true)
        .setContentText(getResources().getText(R.string.ingest_importing));
    startForeground(R.id.ingest_notification_importing,
        mNotificationBuilder.build());
    new Thread(task).start();
  }

  @Override
  public void deviceAdded(MtpDevice device) {
    if (mDevice == null) {
      setDevice(device);
    }
  }

  @Override
  public void deviceRemoved(MtpDevice device) {
    if (device == mDevice) {
      mNotificationManager.cancel(R.id.ingest_notification_scanning);
      mNotificationManager.cancel(R.id.ingest_notification_importing);
      setDevice(null);
      mNeedRelaunchNotification = false;

    }
  }

  @Override
  public void onImportProgress(int visitedCount, int totalCount,
      String pathIfSuccessful) {
    if (pathIfSuccessful != null) {
      mScannerClient.scanPath(pathIfSuccessful);
    }
    mNeedRelaunchNotification = false;
    if (mClientActivity != null) {
      mClientActivity.onImportProgress(visitedCount, totalCount, pathIfSuccessful);
    }
    mNotificationBuilder.setProgress(totalCount, visitedCount, false)
        .setContentText(getResources().getText(R.string.ingest_importing));
    mNotificationManager.notify(R.id.ingest_notification_importing,
        mNotificationBuilder.build());
  }

  @Override
  public void onImportFinish(Collection<IngestObjectInfo> objectsNotImported,
      int visitedCount) {
    stopForeground(true);
    mNeedRelaunchNotification = true;
    if (mClientActivity != null) {
      mClientActivity.onImportFinish(objectsNotImported, visitedCount);
    } else {
      mRedeliverImportFinish = true;
      mRedeliverObjectsNotImported = objectsNotImported;
      mRedeliverImportFinishCount = visitedCount;
      mNotificationBuilder.setProgress(0, 0, false)
          .setContentText(getResources().getText(R.string.ingest_import_complete));
      mNotificationManager.notify(R.id.ingest_notification_importing,
          mNotificationBuilder.build());
    }
  }

  @Override
  public void onObjectIndexed(IngestObjectInfo object, int numVisited) {
    mNeedRelaunchNotification = false;
    if (mClientActivity != null) {
      mClientActivity.onObjectIndexed(object, numVisited);
    } else {
      // Throttle the updates to one every PROGRESS_UPDATE_INTERVAL_MS milliseconds
      long currentTime = SystemClock.uptimeMillis();
      if (currentTime > mLastProgressIndexTime + PROGRESS_UPDATE_INTERVAL_MS) {
        mLastProgressIndexTime = currentTime;
        mNotificationBuilder.setProgress(0, numVisited, true)
            .setContentText(getResources().getText(R.string.ingest_scanning));
        mNotificationManager.notify(R.id.ingest_notification_scanning,
            mNotificationBuilder.build());
      }
    }
  }

  @Override
  public void onSortingStarted() {
    if (mClientActivity != null) {
      mClientActivity.onSortingStarted();
    }
  }

  @Override
  public void onIndexingFinished() {
    mNeedRelaunchNotification = true;
    if (mClientActivity != null) {
      mClientActivity.onIndexingFinished();
    } else {
      mNotificationBuilder.setProgress(0, 0, false)
          .setContentText(getResources().getText(R.string.ingest_scanning_done));
      mNotificationManager.notify(R.id.ingest_notification_scanning,
          mNotificationBuilder.build());
      mRedeliverIndexFinish = true;
    }
  }

  // Copied from old Gallery3d code
  private static final class ScannerClient implements MediaScannerConnectionClient {
    ArrayList<String> mPaths = new ArrayList<String>();
    MediaScannerConnection mScannerConnection;
    boolean mConnected;
    Object mLock = new Object();

    public ScannerClient(Context context) {
      mScannerConnection = new MediaScannerConnection(context, this);
    }

    public void scanPath(String path) {
      synchronized (mLock) {
        if (mConnected) {
          mScannerConnection.scanFile(path, null);
        } else {
          mPaths.add(path);
          mScannerConnection.connect();
        }
      }
    }

    @Override
    public void onMediaScannerConnected() {
      synchronized (mLock) {
        mConnected = true;
        if (!mPaths.isEmpty()) {
          for (String path : mPaths) {
            mScannerConnection.scanFile(path, null);
          }
          mPaths.clear();
        }
      }
    }

    @Override
    public void onScanCompleted(String path, Uri uri) {
    }
  }
}
