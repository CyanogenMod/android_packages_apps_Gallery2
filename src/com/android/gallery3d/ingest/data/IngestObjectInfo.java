package com.android.gallery3d.ingest.data;

import android.annotation.TargetApi;
import android.mtp.MtpDevice;
import android.mtp.MtpObjectInfo;
import android.os.Build;

/**
 * Holds the info needed for the in-memory index of MTP objects.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
public class IngestObjectInfo implements Comparable<IngestObjectInfo> {

  private int mHandle;
  private long mDateCreated;
  private int mFormat;
  private int mCompressedSize;

  public IngestObjectInfo(MtpObjectInfo mtpObjectInfo) {
    mHandle = mtpObjectInfo.getObjectHandle();
    mDateCreated = mtpObjectInfo.getDateCreated();
    mFormat = mtpObjectInfo.getFormat();
    mCompressedSize = mtpObjectInfo.getCompressedSize();
  }

  public IngestObjectInfo(int handle, long dateCreated, int format, int compressedSize) {
    mHandle = handle;
    mDateCreated = dateCreated;
    mFormat = format;
    mCompressedSize = compressedSize;
  }

  public int getCompressedSize() {
    return mCompressedSize;
  }

  public int getFormat() {
    return mFormat;
  }

  public long getDateCreated() {
    return mDateCreated;
  }

  public int getObjectHandle() {
    return mHandle;
  }

  public String getName(MtpDevice device) {
    if (device != null) {
      MtpObjectInfo info = device.getObjectInfo(mHandle);
      if (info != null) {
        return info.getName();
      }
    }
    return null;
  }

  @Override
  public int compareTo(IngestObjectInfo another) {
    long diff = getDateCreated() - another.getDateCreated();
    if (diff < 0) {
      return -1;
    } else if (diff == 0) {
      return 0;
    } else {
      return 1;
    }
  }

  @Override
  public String toString() {
    return "IngestObjectInfo [mHandle=" + mHandle + ", mDateCreated=" + mDateCreated
        + ", mFormat=" + mFormat + ", mCompressedSize=" + mCompressedSize + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + mCompressedSize;
    result = prime * result + (int) (mDateCreated ^ (mDateCreated >>> 32));
    result = prime * result + mFormat;
    result = prime * result + mHandle;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof IngestObjectInfo)) {
      return false;
    }
    IngestObjectInfo other = (IngestObjectInfo) obj;
    if (mCompressedSize != other.mCompressedSize) {
      return false;
    }
    if (mDateCreated != other.mDateCreated) {
      return false;
    }
    if (mFormat != other.mFormat) {
      return false;
    }
    if (mHandle != other.mHandle) {
      return false;
    }
    return true;
  }
}
