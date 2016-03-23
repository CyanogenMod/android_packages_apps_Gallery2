package com.android.gallery3d.ingest.data;

import android.annotation.TargetApi;
import android.mtp.MtpConstants;
import android.mtp.MtpDevice;
import android.mtp.MtpObjectInfo;
import android.os.Build;
import android.webkit.MimeTypeMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Index of MTP media objects organized into "buckets," or groupings, based on the date
 * they were created.
 *
 * When the index is created, the buckets are sorted in their natural
 * order, and the items within the buckets sorted by the date they are taken.
 *
 * The index enables the access of items and bucket labels as one unified list.
 * For example, let's say we have the following data in the index:
 *    [Bucket A]: [photo 1], [photo 2]
 *    [Bucket B]: [photo 3]
 *
 * Then the items can be thought of as being organized as a 5 element list:
 *   [Bucket A], [photo 1], [photo 2], [Bucket B], [photo 3]
 *
 * The data can also be accessed in descending order, in which case the list
 * would be a bit different from simply reversing the ascending list, since the
 * bucket labels need to always be at the beginning:
 *   [Bucket B], [photo 3], [Bucket A], [photo 2], [photo 1]
 *
 * The index enables all the following operations in constant time, both for
 * ascending and descending views of the data:
 *   - get/getAscending/getDescending: get an item at a specified list position
 *   - size: get the total number of items (bucket labels and MTP objects)
 *   - getFirstPositionForBucketNumber
 *   - getBucketNumberForPosition
 *   - isFirstInBucket
 *
 * See {@link MtpDeviceIndexRunnable} for implementation notes.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
public class MtpDeviceIndex {

  /**
   * Indexing progress listener.
   */
  public interface ProgressListener {
    /**
     * A media item on the device was indexed.
     * @param object The media item that was just indexed
     * @param numVisited Number of items visited so far
     */
    public void onObjectIndexed(IngestObjectInfo object, int numVisited);

    /**
     * The metadata loaded from the device is being sorted.
     */
    public void onSortingStarted();

    /**
     * The indexing is done and the index is ready to be used.
     */
    public void onIndexingFinished();
  }

  /**
   * Media sort orders.
   */
  public enum SortOrder {
    ASCENDING, DESCENDING
  }

  /** Quicktime MOV container (not already defined in {@link MtpConstants}) **/
  public static final int FORMAT_MOV = 0x300D;

  public static final Set<Integer> SUPPORTED_IMAGE_FORMATS;
  public static final Set<Integer> SUPPORTED_VIDEO_FORMATS;

  static {
    Set<Integer> supportedImageFormats = new HashSet<Integer>();
    supportedImageFormats.add(MtpConstants.FORMAT_JFIF);
    supportedImageFormats.add(MtpConstants.FORMAT_EXIF_JPEG);
    supportedImageFormats.add(MtpConstants.FORMAT_PNG);
    supportedImageFormats.add(MtpConstants.FORMAT_GIF);
    supportedImageFormats.add(MtpConstants.FORMAT_BMP);
    supportedImageFormats.add(MtpConstants.FORMAT_TIFF);
    supportedImageFormats.add(MtpConstants.FORMAT_TIFF_EP);
    if (Build.VERSION.SDK_INT >= 24) {
      supportedImageFormats.add(MtpConstants.FORMAT_DNG);
    }
    SUPPORTED_IMAGE_FORMATS = Collections.unmodifiableSet(supportedImageFormats);

    Set<Integer> supportedVideoFormats = new HashSet<Integer>();
    supportedVideoFormats.add(MtpConstants.FORMAT_3GP_CONTAINER);
    supportedVideoFormats.add(MtpConstants.FORMAT_AVI);
    supportedVideoFormats.add(MtpConstants.FORMAT_MP4_CONTAINER);
    supportedVideoFormats.add(MtpConstants.FORMAT_MP2);
    supportedVideoFormats.add(MtpConstants.FORMAT_MPEG);
    // TODO(georgescu): add FORMAT_MOV once Android Media Scanner supports .mov files
    SUPPORTED_VIDEO_FORMATS = Collections.unmodifiableSet(supportedVideoFormats);
  }

  private MtpDevice mDevice;
  private long mGeneration;
  private ProgressListener mProgressListener;
  private volatile MtpDeviceIndexRunnable.Results mResults;
  private final MtpDeviceIndexRunnable.Factory mIndexRunnableFactory;

  private static final MtpDeviceIndex sInstance = new MtpDeviceIndex(
      MtpDeviceIndexRunnable.getFactory());

  private static final Map<String, Boolean> sCachedSupportedExtenstions = new HashMap<>();

  public static MtpDeviceIndex getInstance() {
    return sInstance;
  }

  protected MtpDeviceIndex(MtpDeviceIndexRunnable.Factory indexRunnableFactory) {
    mIndexRunnableFactory = indexRunnableFactory;
  }

  public synchronized MtpDevice getDevice() {
    return mDevice;
  }

  public synchronized boolean isDeviceConnected() {
    return (mDevice != null);
  }

  /**
   * @param mtpObjectInfo MTP object info
   * @return Whether the format is supported by this index.
   */
  public boolean isFormatSupported(MtpObjectInfo mtpObjectInfo) {
    // Checks whether the format is supported or not.
    final int format = mtpObjectInfo.getFormat();
    if (SUPPORTED_IMAGE_FORMATS.contains(format)
        || SUPPORTED_VIDEO_FORMATS.contains(format)) {
      return true;
    }

    // Checks whether the extension is supported or not.
    final String name = mtpObjectInfo.getName();
    if (name == null) {
      return false;
    }
    final int lastDot = name.lastIndexOf('.');
    if (lastDot >= 0) {
      final String extension = name.substring(lastDot + 1);

      Boolean result = sCachedSupportedExtenstions.get(extension);
      if (result != null) {
        return result;
      }
      final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
          extension.toLowerCase(Locale.US));
      if (mime != null) {
        // This will also accept the newly added mimetypes for images and videos.
        result = mime.startsWith("image/") || mime.startsWith("video/");
        sCachedSupportedExtenstions.put(extension, result);
        return result;
      }
    }

    return false;
  }

  /**
   * Sets the MtpDevice that should be indexed and initializes state, but does
   * not kick off the actual indexing task, which is instead done by using
   * {@link #getIndexRunnable()}
   *
   * @param device The MtpDevice that should be indexed
   */
  public synchronized void setDevice(MtpDevice device) {
    if (device == mDevice) {
      return;
    }
    mDevice = device;
    resetState();
  }

  /**
   * Provides a Runnable for the indexing task (assuming the state has already
   * been correctly initialized by calling {@link #setDevice(MtpDevice)}).
   *
   * @return Runnable for the main indexing task
   */
  public synchronized Runnable getIndexRunnable() {
    if (!isDeviceConnected() || mResults != null) {
      return null;
    }
    return mIndexRunnableFactory.createMtpDeviceIndexRunnable(this);
  }

  /**
   * @return Whether the index is ready to be used.
   */
  public synchronized boolean isIndexReady() {
    return mResults != null;
  }

  /**
   * @param listener
   * @return Current progress (useful for configuring initial UI state)
   */
  public synchronized void setProgressListener(ProgressListener listener) {
    mProgressListener = listener;
  }

  /**
   * Make the listener null if it matches the argument
   *
   * @param listener Listener to unset, if currently registered
   */
  public synchronized void unsetProgressListener(ProgressListener listener) {
    if (mProgressListener == listener) {
      mProgressListener = null;
    }
  }

  /**
   * @return The total number of elements in the index (labels and items)
   */
  public int size() {
    MtpDeviceIndexRunnable.Results results = mResults;
    return results != null ? results.unifiedLookupIndex.length : 0;
  }

  /**
   * @param position Index of item to fetch, where 0 is the first item in the
   *            specified order
   * @param order
   * @return the bucket label or IngestObjectInfo at the specified position and
   *         order
   */
  public Object get(int position, SortOrder order) {
    MtpDeviceIndexRunnable.Results results = mResults;
    if (results == null) {
      return null;
    }
    if (order == SortOrder.ASCENDING) {
      DateBucket bucket = results.buckets[results.unifiedLookupIndex[position]];
      if (bucket.unifiedStartIndex == position) {
        return bucket.date;
      } else {
        return results.mtpObjects[bucket.itemsStartIndex + position - 1
            - bucket.unifiedStartIndex];
      }
    } else {
      int zeroIndex = results.unifiedLookupIndex.length - 1 - position;
      DateBucket bucket = results.buckets[results.unifiedLookupIndex[zeroIndex]];
      if (bucket.unifiedEndIndex == zeroIndex) {
        return bucket.date;
      } else {
        return results.mtpObjects[bucket.itemsStartIndex + zeroIndex
            - bucket.unifiedStartIndex];
      }
    }
  }

  /**
   * @param position Index of item to fetch from a view of the data that does not
   *            include labels and is in the specified order
   * @return position-th item in specified order, when not including labels
   */
  public IngestObjectInfo getWithoutLabels(int position, SortOrder order) {
    MtpDeviceIndexRunnable.Results results = mResults;
    if (results == null) {
      return null;
    }
    if (order == SortOrder.ASCENDING) {
      return results.mtpObjects[position];
    } else {
      return results.mtpObjects[results.mtpObjects.length - 1 - position];
    }
  }

  /**
   * @param position Index of item to map from a view of the data that does not
   *            include labels and is in the specified order
   * @param order
   * @return position in a view of the data that does include labels, or -1 if the index isn't
   *         ready
   */
  public int getPositionFromPositionWithoutLabels(int position, SortOrder order) {
        /* Although this is O(log(number of buckets)), and thus should not be used
           in hotspots, even if the attached device has items for every day for
           a five-year timeframe, it would still only take 11 iterations at most,
           so shouldn't be a huge issue. */
    MtpDeviceIndexRunnable.Results results = mResults;
    if (results == null) {
      return -1;
    }
    if (order == SortOrder.DESCENDING) {
      position = results.mtpObjects.length - 1 - position;
    }
    int bucketNumber = 0;
    int iMin = 0;
    int iMax = results.buckets.length - 1;
    while (iMax >= iMin) {
      int iMid = (iMax + iMin) / 2;
      if (results.buckets[iMid].itemsStartIndex + results.buckets[iMid].numItems
          <= position) {
        iMin = iMid + 1;
      } else if (results.buckets[iMid].itemsStartIndex > position) {
        iMax = iMid - 1;
      } else {
        bucketNumber = iMid;
        break;
      }
    }
    int mappedPos = results.buckets[bucketNumber].unifiedStartIndex + position
        - results.buckets[bucketNumber].itemsStartIndex + 1;
    if (order == SortOrder.DESCENDING) {
      mappedPos = results.unifiedLookupIndex.length - mappedPos;
    }
    return mappedPos;
  }

  /**
   * @param position Index of item to map from a view of the data that
   *            includes labels and is in the specified order
   * @param order
   * @return position in a view of the data that does not include labels, or -1 if the index isn't
   *         ready
   */
  public int getPositionWithoutLabelsFromPosition(int position, SortOrder order) {
    MtpDeviceIndexRunnable.Results results = mResults;
    if (results == null) {
      return -1;
    }
    if (order == SortOrder.ASCENDING) {
      DateBucket bucket = results.buckets[results.unifiedLookupIndex[position]];
      if (bucket.unifiedStartIndex == position) {
        position++;
      }
      return bucket.itemsStartIndex + position - 1 - bucket.unifiedStartIndex;
    } else {
      int zeroIndex = results.unifiedLookupIndex.length - 1 - position;
      DateBucket bucket = results.buckets[results.unifiedLookupIndex[zeroIndex]];
      if (bucket.unifiedEndIndex == zeroIndex) {
        zeroIndex--;
      }
      return results.mtpObjects.length - 1 - bucket.itemsStartIndex
          - zeroIndex + bucket.unifiedStartIndex;
    }
  }

  /**
   * @return The number of media items in the index
   */
  public int sizeWithoutLabels() {
    MtpDeviceIndexRunnable.Results results = mResults;
    return results != null ? results.mtpObjects.length : 0;
  }

  /**
   * @param bucketNumber Index of bucket in the specified order
   * @param order
   * @return position of bucket's first item in a view of the data that includes labels
   */
  public int getFirstPositionForBucketNumber(int bucketNumber, SortOrder order) {
    MtpDeviceIndexRunnable.Results results = mResults;
    if (order == SortOrder.ASCENDING) {
      return results.buckets[bucketNumber].unifiedStartIndex;
    } else {
      return results.unifiedLookupIndex.length
          - results.buckets[results.buckets.length - 1 - bucketNumber].unifiedEndIndex
          - 1;
    }
  }

  /**
   * @param position Index of item in the view of the data that includes labels and is in
   *                 the specified order
   * @param order
   * @return Index of the bucket that contains the specified item
   */
  public int getBucketNumberForPosition(int position, SortOrder order) {
    MtpDeviceIndexRunnable.Results results = mResults;
    if (order == SortOrder.ASCENDING) {
      return results.unifiedLookupIndex[position];
    } else {
      return results.buckets.length - 1
          - results.unifiedLookupIndex[results.unifiedLookupIndex.length - 1
          - position];
    }
  }

  /**
   * @param position Index of item in the view of the data that includes labels and is in
   *                 the specified order
   * @param order
   * @return Whether the specified item is the first item in its bucket
   */
  public boolean isFirstInBucket(int position, SortOrder order) {
    MtpDeviceIndexRunnable.Results results = mResults;
    if (order == SortOrder.ASCENDING) {
      return results.buckets[results.unifiedLookupIndex[position]].unifiedStartIndex
          == position;
    } else {
      position = results.unifiedLookupIndex.length - 1 - position;
      return results.buckets[results.unifiedLookupIndex[position]].unifiedEndIndex
          == position;
    }
  }

  /**
   * @param order
   * @return Array of buckets in the specified order
   */
  public DateBucket[] getBuckets(SortOrder order) {
    MtpDeviceIndexRunnable.Results results = mResults;
    if (results == null) {
      return null;
    }
    return (order == SortOrder.ASCENDING) ? results.buckets : results.reversedBuckets;
  }

  protected void resetState() {
    mGeneration++;
    mResults = null;
  }

  /**
   * @param device
   * @param generation
   * @return whether the index is at the given generation and the given device is connected
   */
  protected boolean isAtGeneration(MtpDevice device, long generation) {
    return (mGeneration == generation) && (mDevice == device);
  }

  protected synchronized boolean setIndexingResults(MtpDevice device, long generation,
      MtpDeviceIndexRunnable.Results results) {
    if (!isAtGeneration(device, generation)) {
      return false;
    }
    mResults = results;
    onIndexFinish(true /*successful*/);
    return true;
  }

  protected synchronized void onIndexFinish(boolean successful) {
    if (!successful) {
      resetState();
    }
    if (mProgressListener != null) {
      mProgressListener.onIndexingFinished();
    }
  }

  protected synchronized void onSorting() {
    if (mProgressListener != null) {
      mProgressListener.onSortingStarted();
    }
  }

  protected synchronized void onObjectIndexed(IngestObjectInfo object, int numVisited) {
    if (mProgressListener != null) {
      mProgressListener.onObjectIndexed(object, numVisited);
    }
  }

  protected long getGeneration() {
    return mGeneration;
  }
}
