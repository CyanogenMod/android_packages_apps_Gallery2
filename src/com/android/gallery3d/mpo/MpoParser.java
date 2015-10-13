/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.gallery3d.mpo;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.exif.CountedDataInputStream;
import com.android.gallery3d.filtershow.tools.DualCameraNativeEngine;

public class MpoParser {
    private static final String LOGTAG = "MpoParser";
    private static final int MP_INDEX_FIELD_SIZE_BYTES = 12;

    protected static final short LITTLE_ENDIAN_TAG = 0x4949; // "II"
    protected static final short BIG_ENDIAN_TAG = 0x4d4d; // "MM"

    private ContentResolver mContentResolver;
    private Uri mUri;

    private CountedDataInputStream mDataStream;
    private ByteOrder mByteOrder;
    private int mMpHeaderOffset;
    private int mIfd1Offset;
    private int mMpEntryOffset;
    private MpEntry mPrimaryEntry;
    private MpEntry mSecondaryEntry;

    private MpoParser(Context context, Uri uri) {
        mContentResolver = context.getContentResolver();
        mUri = uri;

        InputStream is = null;
        try {
            is = mContentResolver.openInputStream(mUri);
            // seek to mp header
            if((mMpHeaderOffset = seekToMpData(is)) == -1)
                return;

            // read mp header
            mDataStream = new CountedDataInputStream(is);
            readMpHeader();

            // read mp index ifd
            readMpIndexIfdData();

            // read mp entries
            readMpEntryData();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Utils.closeSilently(is);
        }
    }

    public static MpoParser parse(Context context, Uri uri) {
        return new MpoParser(context, uri);
    }

    private int seekToMpData(InputStream inputStream) throws IOException {
        CountedDataInputStream dataStream = new CountedDataInputStream(inputStream);
        if (dataStream.readShort() != MpoHeader.SOI) {
            return -1;
        }

        short marker = dataStream.readShort();
        while (marker != MpoHeader.EOI
                && !MpoHeader.isSofMarker(marker)) {
            int length = dataStream.readUnsignedShort();
            if (marker == MpoHeader.APP2) {
                dataStream.readInt();    // MP Format Identifier
                return dataStream.getReadByteCount();
            }
            if (length < 2 || (length - 2) != dataStream.skip(length - 2)) {
                Log.w(LOGTAG, "Invalid JPEG format.");
                return -1;
            }
            marker = dataStream.readShort();
        }
        return -1;
    }

    private void readMpHeader() throws IOException {
        short byteOrder = mDataStream.readShort();
        mByteOrder = (byteOrder == LITTLE_ENDIAN_TAG)?ByteOrder.LITTLE_ENDIAN:ByteOrder.BIG_ENDIAN;
        mDataStream.setByteOrder(mByteOrder);
        if(mDataStream.readShort() == 42) {
            Log.v(LOGTAG, "correct endian!");
        } else {
            Log.w(LOGTAG, "incorrect endian!");
        }

        mIfd1Offset = mDataStream.readInt();
    }

    private void readMpIndexIfdData() throws IOException {
        mDataStream.skipTo(mIfd1Offset);
        int count = mDataStream.readShort();
        // add 6 (2 for count, 4 for offset to next IFD)
        mMpEntryOffset = mIfd1Offset + (count*MP_INDEX_FIELD_SIZE_BYTES) + 6;
    }

    private MpEntry readMpEntryData() {
        try {
            mDataStream.skipTo(mMpEntryOffset);

            mPrimaryEntry = new MpEntry();
            mPrimaryEntry.mImgAttribute = mDataStream.readInt();
            mPrimaryEntry.mImgSize = mDataStream.readInt();
            mPrimaryEntry.mImgDataOffset = mDataStream.readInt();
            mPrimaryEntry.mDepImg1Entry = mDataStream.readShort();
            mPrimaryEntry.mDepImg2Entry = mDataStream.readShort();

            mSecondaryEntry = new MpEntry();
            mSecondaryEntry.mImgAttribute = mDataStream.readInt();
            mSecondaryEntry.mImgSize = mDataStream.readInt();
            mSecondaryEntry.mImgDataOffset = mDataStream.readInt();
            mSecondaryEntry.mDepImg1Entry = mDataStream.readShort();
            mSecondaryEntry.mDepImg2Entry = mDataStream.readShort();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return mPrimaryEntry;
    }

    public byte[] readImgData(boolean primary) {
        MpEntry mpEntry = null;
        byte[] data = null;

        if(primary) {
            mpEntry = mPrimaryEntry;
        } else {
            mpEntry = mSecondaryEntry;
        }

        if(mpEntry == null)
            return data;

        InputStream is = null;
        ByteArrayOutputStream baos = null;
        try {
            is = mContentResolver.openInputStream(mUri);
            data = new byte[mpEntry.mImgSize];

            is.skip((mpEntry.mImgDataOffset>0)?mpEntry.mImgDataOffset + mMpHeaderOffset : mpEntry.mImgDataOffset);
            if(is.read(data) == -1) {
                Log.d(LOGTAG, "read EOF. invalid offset/size");
                data = null;
            } else {
                // verify we have well formed jpeg data
                ByteBuffer buffer = ByteBuffer.wrap(data);
                if(buffer.getShort(0) != MpoHeader.SOI) {
                    Log.d(LOGTAG, "non valid SOI. offset incorrect.");
                    data = null;
                } else if(buffer.getShort(buffer.limit() - 2) != MpoHeader.EOI) {
                    Log.d(LOGTAG, "non valid EOI. size incorrect. attempting to read further till EOI");
                    baos = new ByteArrayOutputStream(data.length);
                    baos.write(data);

                    byte[] readArray = new byte[2];
                    ByteBuffer readBuf = ByteBuffer.wrap(readArray);

                    readArray[0] = data[data.length-2];
                    readArray[1] = data[data.length-1];

                    data = null;

                    boolean validJpg = true;
                    while(readBuf.getShort(0) != MpoHeader.EOI) {
                        int read = is.read();
                        if(read == -1) {
                            Log.d(LOGTAG, "reached EOF before EOI. invalid file");
                            validJpg = false;
                            break;
                        }
                        readArray[0] = readArray[1];
                        readArray[1] = (byte) (read & 0xFF);

                        baos.write(read);
                    }

                    if(validJpg)
                        data = baos.toByteArray();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Utils.closeSilently(is);
            Utils.closeSilently(baos);
        }

        return data;
    }

    public static String getDepthmapFilepath(String mpoFilepath) {
        String depthFilepath = mpoFilepath.substring(0, mpoFilepath.lastIndexOf('.'))
                + DualCameraNativeEngine.DEPTH_MAP_EXT;

        return depthFilepath;
    }

    public static void writeDepthMapFile(String depthMapFilepath, ByteBuffer depthMap) {
        FileChannel fc = null;
        try {
            fc = new FileOutputStream(depthMapFilepath).getChannel();
            fc.write(depthMap);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(fc != null) {
                try {
                    fc.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        depthMap.clear();
    }

    public static ByteBuffer readDepthMapFile(String depthMapFilepath) {
        FileChannel fc = null;
        ByteBuffer depthMap = null;
        try {
            fc = new FileInputStream(depthMapFilepath).getChannel();
            depthMap = ByteBuffer.allocateDirect((int)fc.size());
            fc.read(depthMap);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(fc != null) {
                try {
                    fc.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return depthMap;
    }

    class MpEntry {
        int mImgAttribute;
        int mImgSize;
        int mImgDataOffset;
        short mDepImg1Entry;
        short mDepImg2Entry;
    }
}