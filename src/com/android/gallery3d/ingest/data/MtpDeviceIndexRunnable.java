package com.android.gallery3d.ingest.data;

import android.annotation.TargetApi;
import android.mtp.MtpConstants;
import android.mtp.MtpDevice;
import android.mtp.MtpObjectInfo;
import android.os.Build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.Stack;
import java.util.TreeMap;

/**
 * Runnable used by the {@link MtpDeviceIndex} to populate its index.
 *
 * Implementation note: this is the way the index supports a lot of its operations in
 * constant time and respecting the need to have bucket names always come before items
 * in that bucket when accessing the list sequentially, both in ascending and descending
 * orders.
 *
 * Let's say the data we have in the index is the following:
 *  [Bucket A]: [photo 1], [photo 2]
 *  [Bucket B]: [photo 3]
 *
 *  In this case, the lookup index array would be
 *  [0, 0, 0, 1, 1]
 *
 *  Now, whether we access the list in ascending or descending order, we know which bucket
 *  to look in (0 corresponds to A and 1 to B), and can return the bucket label as the first
 *  item in a bucket as needed. The individual IndexBUckets have a startIndex and endIndex
 *  that correspond to indices in this lookup index array, allowing us to calculate the
 *  offset of the specific item we want from within a specific bucket.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
public class MtpDeviceIndexRunnable implements Runnable {

  /**
   * MtpDeviceIndexRunnable factory.
   */
  public static class Factory {
    public MtpDeviceIndexRunnable createMtpDeviceIndexRunnable(MtpDeviceIndex index) {
      return new MtpDeviceIndexRunnable(index);
    }
  }

  static class Results {
    final int[] unifiedLookupIndex;
    final IngestObjectInfo[] mtpObjects;
    final DateBucket[] buckets;
    final DateBucket[] reversedBuckets;

    public Results(
        int[] unifiedLookupIndex, IngestObjectInfo[] mtpObjects, DateBucket[] buckets) {
      this.unifiedLookupIndex = unifiedLookupIndex;
      this.mtpObjects = mtpObjects;
      this.buckets = buckets;
      this.reversedBuckets = new DateBucket[buckets.length];
      for (int i = 0; i < buckets.length; i++) {
        this.reversedBuckets[i] = buckets[buckets.length - 1 - i];
      }
    }
  }

  private final MtpDevice mDevice;
  protected final MtpDeviceIndex mIndex;
  private final long mIndexGeneration;

  private static Factory sDefaultFactory = new Factory();

  public static Factory getFactory() {
    return sDefaultFactory;
  }

  /**
   * Exception thrown when a problem occurred during indexing.
   */
  @SuppressWarnings("serial")
  public class IndexingException extends RuntimeException {}

  MtpDeviceIndexRunnable(MtpDeviceIndex index) {
    mIndex = index;
    mDevice = index.getDevice();
    mIndexGeneration = index.getGeneration();
  }

  @Override
  public void run() {
    try {
      indexDevice();
    } catch (IndexingException e) {
      mIndex.onIndexFinish(false /*successful*/);
    }
  }

  private void indexDevice() throws IndexingException {
    SortedMap<SimpleDate, List<IngestObjectInfo>> bucketsTemp =
        new TreeMap<SimpleDate, List<IngestObjectInfo>>();
    int numObjects = addAllObjects(bucketsTemp);
    mIndex.onSorting();
    int numBuckets = bucketsTemp.size();
    DateBucket[] buckets = new DateBucket[numBuckets];
    IngestObjectInfo[] mtpObjects = new IngestObjectInfo[numObjects];
    int[] unifiedLookupIndex = new int[numObjects + numBuckets];
    int currentUnifiedIndexEntry = 0;
    int currentItemsEntry = 0;
    int nextUnifiedEntry, unifiedStartIndex, numBucketObjects, unifiedEndIndex, itemsStartIndex;

    int i = 0;
    for (Map.Entry<SimpleDate, List<IngestObjectInfo>> bucketTemp : bucketsTemp.entrySet()) {
      List<IngestObjectInfo> objects = bucketTemp.getValue();
      Collections.sort(objects);
      numBucketObjects = objects.size();

      nextUnifiedEntry = currentUnifiedIndexEntry + numBucketObjects + 1;
      Arrays.fill(unifiedLookupIndex, currentUnifiedIndexEntry, nextUnifiedEntry, i);
      unifiedStartIndex = currentUnifiedIndexEntry;
      unifiedEndIndex = nextUnifiedEntry - 1;
      currentUnifiedIndexEntry = nextUnifiedEntry;

      itemsStartIndex = currentItemsEntry;
      for (int j = 0; j < numBucketObjects; j++) {
        mtpObjects[currentItemsEntry] = objects.get(j);
        currentItemsEntry++;
      }
      buckets[i] = new DateBucket(bucketTemp.getKey(), unifiedStartIndex, unifiedEndIndex,
          itemsStartIndex, numBucketObjects);
      i++;
    }
    if (!mIndex.setIndexingResults(mDevice, mIndexGeneration,
        new Results(unifiedLookupIndex, mtpObjects, buckets))) {
      throw new IndexingException();
    }
  }

  private SimpleDate mDateInstance = new SimpleDate();

  protected void addObject(IngestObjectInfo objectInfo,
      SortedMap<SimpleDate, List<IngestObjectInfo>> bucketsTemp, int numObjects) {
    mDateInstance.setTimestamp(objectInfo.getDateCreated());
    List<IngestObjectInfo> bucket = bucketsTemp.get(mDateInstance);
    if (bucket == null) {
      bucket = new ArrayList<IngestObjectInfo>();
      bucketsTemp.put(mDateInstance, bucket);
      mDateInstance = new SimpleDate(); // only create new date objects when they are used
    }
    bucket.add(objectInfo);
    mIndex.onObjectIndexed(objectInfo, numObjects);
  }

  protected int addAllObjects(SortedMap<SimpleDate, List<IngestObjectInfo>> bucketsTemp)
      throws IndexingException {
    int numObjects = 0;
    for (int storageId : mDevice.getStorageIds()) {
      if (!mIndex.isAtGeneration(mDevice, mIndexGeneration)) {
        throw new IndexingException();
      }
      Stack<Integer> pendingDirectories = new Stack<Integer>();
      pendingDirectories.add(0xFFFFFFFF); // start at the root of the device
      while (!pendingDirectories.isEmpty()) {
        if (!mIndex.isAtGeneration(mDevice, mIndexGeneration)) {
          throw new IndexingException();
        }
        int dirHandle = pendingDirectories.pop();
        for (int objectHandle : mDevice.getObjectHandles(storageId, 0, dirHandle)) {
          MtpObjectInfo mtpObjectInfo = mDevice.getObjectInfo(objectHandle);
          if (mtpObjectInfo == null) {
            throw new IndexingException();
          }
          int format = mtpObjectInfo.getFormat();
          if (format == MtpConstants.FORMAT_ASSOCIATION) {
            pendingDirectories.add(objectHandle);
          } else if (mIndex.isFormatSupported(format)) {
            numObjects++;
            addObject(new IngestObjectInfo(mtpObjectInfo), bucketsTemp, numObjects);
          }
        }
      }
    }
    return numObjects;
  }
}
