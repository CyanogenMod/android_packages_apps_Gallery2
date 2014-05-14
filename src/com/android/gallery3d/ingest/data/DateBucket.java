package com.android.gallery3d.ingest.data;

import android.annotation.TargetApi;
import android.os.Build;

/**
 * Date bucket for {@link MtpDeviceIndex}.
 * See {@link MtpDeviceIndexRunnable} for implementation notes.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
class DateBucket implements Comparable<DateBucket> {
  final SimpleDate date;
  final int unifiedStartIndex;
  final int unifiedEndIndex;
  final int itemsStartIndex;
  final int numItems;

  public DateBucket(SimpleDate date, int unifiedStartIndex, int unifiedEndIndex,
      int itemsStartIndex, int numItems) {
    this.date = date;
    this.unifiedStartIndex = unifiedStartIndex;
    this.unifiedEndIndex = unifiedEndIndex;
    this.itemsStartIndex = itemsStartIndex;
    this.numItems = numItems;
  }

  @Override
  public String toString() {
    return date.toString();
  }

  @Override
  public int hashCode() {
    return date.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof DateBucket)) {
      return false;
    }
    DateBucket other = (DateBucket) obj;
    if (date == null) {
      if (other.date != null) {
        return false;
      }
    } else if (!date.equals(other.date)) {
      return false;
    }
    return true;
  }

  @Override
  public int compareTo(DateBucket another) {
    return this.date.compareTo(another.date);
  }
}