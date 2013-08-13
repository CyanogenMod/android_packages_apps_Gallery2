package com.android.gallery3d.util;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class GifDecoder extends Thread {

    public static final int STATUS_PARSING = 0;
    public static final int STATUS_FORMAT_ERROR = 1;
    public static final int STATUS_OPEN_ERROR = 2;
    public static final int STATUS_FINISH = -1;

    private InputStream mIS;
    private int mStatus;

    public int mWidth; // full image width
    public int mHeight; // full image height
    private boolean mGctFlag; // global color table used
    private int mGctSize; // size of global color table
    private int mLoopCount = 1; // iterations; 0 = repeat forever

    private int[] mGct; // global color table
    private int[] mLct; // local color table
    private int[] mAct; // active color table

    private int mBgIndex; // background color index
    private int mBgColor; // background color
    private int mLastBgColor; // previous bg color
    private int mPixelAspect; // pixel aspect ratio

    private boolean mLctFlag; // local color table flag
    private boolean mInterlace; // interlace flag
    private int mLctSize; // local color table size

    private int mIx, mIy, mIw, mIh; // current image rectangle
    private int mLrx, mLry, mLrw, mLrh;
    private Bitmap mImage; // current frame
    private Bitmap mLastImage; // previous frame
    private GifFrame mCurrentFrame = null;

    private boolean mIsShow = false;

    private byte[] mBlock = new byte[256]; // current data block
    private int mBlockSize = 0; // block size
    private int mDispose = 0;
    private int mLastDispose = 0;
    private boolean mTransparency = false; // use transparent color
    private int mDelay = 0; // delay in milliseconds
    private int mTransIndex; // transparent color index

    // max decoder pixel stack size
    private static final int MaxStackSize = 4096;

    // LZW decoder working arrays
    private short[] mPrefix;
    private byte[] mSuffix;
    private byte[] mPixelStack;
    private byte[] mPixels;

    private GifFrame mGifFrame; // frames read from current file
    private int mFrameCount;

    private GifAction mGifAction = null;

    private byte[] mGifData = null;

    public GifDecoder(byte[] data, GifAction act) {
        mGifData = data;
        mGifAction = act;
    }

    public GifDecoder(InputStream is, GifAction act) {
        mIS = is;
        mGifAction = act;
    }

    public void run() {
        if (mIS != null) {
            readStream();
        } else if (mGifData != null) {
            readByte();
        }
    }

    public void free() {
        freeFrame();
        freeIS();
        freeImage();
    }

    public int getStatus() {
        return mStatus;
    }

    public boolean parseOk() {
        return mStatus == STATUS_FINISH;
    }

    public int getDelay(int n) {
        mDelay = -1;
        if ((n >= 0) && (n < mFrameCount)) {
            GifFrame f = getFrame(n);
            if (f != null) {
                mDelay = f.mDelayInMs;
            }
        }
        return mDelay;
    }

    public int[] getDelays() {
        GifFrame f = mGifFrame;
        int[] d = new int[mFrameCount];
        int i = 0;
        while (f != null && i < mFrameCount) {
            d[i] = f.mDelayInMs;
            f = f.mNextFrame;
            i++;
        }
        return d;
    }

    public int getFrameCount() {
        return mFrameCount;
    }

    public Bitmap getImage() {
        return getFrameImage(0);
    }

    public int getLoopCount() {
        return mLoopCount;
    }

    private void setPixels() {
        int[] dest = new int[mWidth * mHeight];
        // fill in starting image contents based on last image's dispose code
        if (mLastDispose > 0) {
            if (mLastDispose == 3) {
                // use image before last
                int n = mFrameCount - 2;
                if (n > 0) {
                    mLastImage = getPreUndisposedImage(n - 1);
                } else {
                    mLastImage = null;
                }
            }
            if (mLastImage != null) {
                mLastImage.getPixels(dest, 0, mWidth, 0, 0, mWidth, mHeight);
                // copy pixels
                if (mLastDispose == 2) {
                    // fill last image rect area with background color
                    int c = 0;
                    if (!mTransparency) {
                        c = mLastBgColor;
                    }
                    for (int i = 0; i < mLrh; i++) {
                        int n1 = (mLry + i) * mWidth + mLrx;
                        int n2 = n1 + mLrw;
                        for (int k = n1; k < n2; k++) {
                            dest[k] = c;
                        }
                    }
                }
            }
        }

        // copy each source line to the appropriate place in the destination
        int pass = 1;
        int inc = 8;
        int iline = 0;
        for (int i = 0; i < mIh; i++) {
            int line = i;
            if (mInterlace) {
                if (iline >= mIh) {
                    pass++;
                    switch (pass) {
                        case 2:
                            iline = 4;
                            break;
                        case 3:
                            iline = 2;
                            inc = 4;
                            break;
                        case 4:
                            iline = 1;
                            inc = 2;
                    }
                }
                line = iline;
                iline += inc;
            }
            line += mIy;
            if (line < mHeight) {
                int k = line * mWidth;
                int dx = k + mIx; // start of line in dest
                int dlim = dx + mIw; // end of dest line
                if ((k + mWidth) < dlim) {
                    dlim = k + mWidth; // past dest edge
                }
                int sx = i * mIw; // start of line in source
                while (dx < dlim) {
                    // map color and insert in destination
                    int index = ((int) mPixels[sx++]) & 0xff;
                    int c = mAct[index];
                    if (c != 0) {
                        dest[dx] = c;
                    }
                    dx++;
                }
            }
        }
        mImage = Bitmap.createBitmap(dest, mWidth, mHeight, Config.ARGB_4444);
    }

    public Bitmap getFrameImage(int n) {
        GifFrame frame = getFrame(n);
        if (frame == null) {
            return null;
        } else {
            return frame.mImage;
        }
    }

    public GifFrame getCurrentFrame() {
        return mCurrentFrame;
    }

    public GifFrame getFrame(int n) {
        GifFrame frame = mGifFrame;
        int i = 0;
        while (frame != null) {
            if (i == n) {
                return frame;
            } else {
                frame = frame.mNextFrame;
            }
            i++;
        }
        return null;
    }

    private Bitmap getPreUndisposedImage(int n) {
        Bitmap preUndisposedImage = null;
        GifFrame frame = mGifFrame;
        int i = 0;
        while (frame != null && i <= n) {
            if (frame.mDispose == 1) {
                preUndisposedImage = frame.mImage;
            } else {
                frame = frame.mNextFrame;
            }
            i++;
        }
        return preUndisposedImage;
    }

    public void reset() {
        mCurrentFrame = mGifFrame;
    }

    public GifFrame next() {
        if (mIsShow == false) {
            mIsShow = true;
            return mGifFrame;
        } else {
            if (mStatus == STATUS_PARSING) {
                if (mCurrentFrame.mNextFrame != null) {
                    mCurrentFrame = mCurrentFrame.mNextFrame;
                }
            } else {
                mCurrentFrame = mCurrentFrame.mNextFrame;
                if (mCurrentFrame == null) {
                    mCurrentFrame = mGifFrame;
                }
            }
            return mCurrentFrame;
        }
    }

    private int readByte() {
        mIS = new ByteArrayInputStream(mGifData);
        mGifData = null;
        return readStream();
    }

    private int readStream() {
        init();
        if (mIS != null) {
            readHeader();
            if (!err()) {
                readContents();
                if (mFrameCount < 0) {
                    mStatus = STATUS_FORMAT_ERROR;
                    mGifAction.parseOk(false, -1);
                } else {
                    mStatus = STATUS_FINISH;
                    mGifAction.parseOk(true, -1);
                }
            }
            try {
                mIS.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            mStatus = STATUS_OPEN_ERROR;
            mGifAction.parseOk(false, -1);
        }
        return mStatus;
    }

    private void decodeImageData() {
        int NullCode = -1;
        int npix = mIw * mIh;
        int available, clear, code_mask, code_size, end_of_information, in_code, old_code,
                bits, code, count, i, datum, data_size, first, top, bi, pi;

        if ((mPixels == null) || (mPixels.length < npix)) {
            mPixels = new byte[npix]; // allocate new pixel array
        }
        if (mPrefix == null) {
            mPrefix = new short[MaxStackSize];
        }
        if (mSuffix == null) {
            mSuffix = new byte[MaxStackSize];
        }
        if (mPixelStack == null) {
            mPixelStack = new byte[MaxStackSize + 1];
        }
        // Initialize GIF data stream decoder.
        data_size = read();
        clear = 1 << data_size;
        end_of_information = clear + 1;
        available = clear + 2;
        old_code = NullCode;
        code_size = data_size + 1;
        code_mask = (1 << code_size) - 1;
        for (code = 0; code < clear; code++) {
            mPrefix[code] = 0;
            mSuffix[code] = (byte) code;
        }

        // Decode GIF pixel stream.
        datum = bits = count = first = top = pi = bi = 0;
        for (i = 0; i < npix;) {
            if (top == 0) {
                if (bits < code_size) {
                    // Load bytes until there are enough bits for a code.
                    if (count == 0) {
                        // Read a new data block.
                        count = readBlock();
                        if (count <= 0) {
                            break;
                        }
                        bi = 0;
                    }
                    datum += (((int) mBlock[bi]) & 0xff) << bits;
                    bits += 8;
                    bi++;
                    count--;
                    continue;
                }
                // Get the next code.
                code = datum & code_mask;
                datum >>= code_size;
                bits -= code_size;

                // Interpret the code
                if ((code > available) || (code == end_of_information)) {
                    break;
                }
                if (code == clear) {
                    // Reset decoder.
                    code_size = data_size + 1;
                    code_mask = (1 << code_size) - 1;
                    available = clear + 2;
                    old_code = NullCode;
                    continue;
                }
                if (old_code == NullCode) {
                    mPixelStack[top++] = mSuffix[code];
                    old_code = code;
                    first = code;
                    continue;
                }
                in_code = code;
                if (code == available) {
                    mPixelStack[top++] = (byte) first;
                    code = old_code;
                }
                while (code > clear) {
                    mPixelStack[top++] = mSuffix[code];
                    code = mPrefix[code];
                }
                first = ((int) mSuffix[code]) & 0xff;
                // Add a new string to the string table,
                if (available >= MaxStackSize) {
                    break;
                }
                mPixelStack[top++] = (byte) first;
                mPrefix[available] = (short) old_code;
                mSuffix[available] = (byte) first;
                available++;
                if (((available & code_mask) == 0)
                        && (available < MaxStackSize)) {
                    code_size++;
                    code_mask += available;
                }
                old_code = in_code;
            }

            // Pop a pixel off the pixel stack.
            top--;
            mPixels[pi++] = mPixelStack[top];
            i++;
        }
        for (i = pi; i < npix; i++) {
            mPixels[i] = 0; // clear missing pixels
        }
    }

    private boolean err() {
        return mStatus != STATUS_PARSING;
    }

    private void init() {
        mStatus = STATUS_PARSING;
        mFrameCount = 0;
        mGifFrame = null;
        mGct = null;
        mLct = null;
    }

    private int read() {
        int curByte = 0;
        try {
            curByte = mIS.read();
        } catch (Exception e) {
            mStatus = STATUS_FORMAT_ERROR;
        }
        return curByte;
    }

    private int readBlock() {
        mBlockSize = read();
        int n = 0;
        if (mBlockSize > 0) {
            try {
                int count = 0;
                while (n < mBlockSize) {
                    count = mIS.read(mBlock, n, mBlockSize - n);
                    if (count == -1) {
                        break;
                    }
                    n += count;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (n < mBlockSize) {
                mStatus = STATUS_FORMAT_ERROR;
            }
        }
        return n;
    }

    private int[] readColorTable(int ncolors) {
        int nbytes = 3 * ncolors;
        int[] tab = null;
        byte[] c = new byte[nbytes];
        int n = 0;
        try {
            n = mIS.read(c);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (n < nbytes) {
            mStatus = STATUS_FORMAT_ERROR;
        } else {
            tab = new int[256]; // max size to avoid bounds checks
            int i = 0;
            int j = 0;
            while (i < ncolors) {
                int r = ((int) c[j++]) & 0xff;
                int g = ((int) c[j++]) & 0xff;
                int b = ((int) c[j++]) & 0xff;
                tab[i++] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }
        return tab;
    }

    private void readContents() {
        // read GIF file content blocks
        boolean done = false;
        while (!(done || err())) {
            int code = read();
            switch (code) {
                case 0x2C: // image separator
                    readImage();
                    break;
                case 0x21: // extension
                    code = read();
                    switch (code) {
                        case 0xf9: // graphics control extension
                            readGraphicControlExt();
                            break;
                        case 0xff: // application extension
                            readBlock();
                            String app = "";
                            for (int i = 0; i < 11; i++) {
                                app += (char) mBlock[i];
                            }
                            if (app.equals("NETSCAPE2.0")) {
                                readNetscapeExt();
                            } else {
                                skip(); // don't care
                            }
                            break;
                        default: // uninteresting extension
                            skip();
                    }
                    break;
                case 0x3b: // terminator
                    done = true;
                    break;
                case 0x00: // bad byte, but keep going and see what happens
                    break;
                default:
                    mStatus = STATUS_FORMAT_ERROR;
            }
        }
    }

    private void readGraphicControlExt() {
        read(); // block size
        int packed = read(); // packed fields
        mDispose = (packed & 0x1c) >> 2; // disposal method
        if (mDispose == 0) {
            mDispose = 1; // elect to keep old image if discretionary
        }
        mTransparency = (packed & 1) != 0;
        mDelay = readShort() * 10; // delay in milliseconds
        mTransIndex = read(); // transparent color index
        read(); // block terminator
    }

    private void readHeader() {
        String id = "";
        for (int i = 0; i < 6; i++) {
            id += (char) read();
        }
        if (!id.startsWith("GIF")) {
            mStatus = STATUS_FORMAT_ERROR;
            return;
        }
        readLSD();
        if (mGctFlag && !err()) {
            mGct = readColorTable(mGctSize);
            mBgColor = mGct[mBgIndex];
        }
    }

    private void readImage() {
        mIx = readShort(); // (sub)image position & size
        mIy = readShort();
        mIw = readShort();
        mIh = readShort();
        int packed = read();
        mLctFlag = (packed & 0x80) != 0; // 1 - local color table flag
        mInterlace = (packed & 0x40) != 0; // 2 - interlace flag
        // 3 - sort flag
        // 4-5 - reserved
        mLctSize = 2 << (packed & 7); // 6-8 - local color table size
        if (mLctFlag) {
            mLct = readColorTable(mLctSize); // read table
            mAct = mLct; // make local table active
        } else {
            mAct = mGct; // make global table active
            if (mBgIndex == mTransIndex) {
                mBgColor = 0;
            }
        }
        int save = 0;
        if (mTransparency) {
            save = mAct[mTransIndex];
            mAct[mTransIndex] = 0; // set transparent color if specified
        }
        if (mAct == null) {
            mStatus = STATUS_FORMAT_ERROR; // no color table defined
        }
        if (err()) {
            return;
        }
        try {
            decodeImageData(); // decode pixel data
            skip();
            if (err()) {
                return;
            }
            mFrameCount++;
            // create new image to receive frame data
            mImage = Bitmap.createBitmap(mWidth, mHeight, Config.ARGB_4444);
            // createImage(mWidth, mHeight);
            setPixels(); // transfer pixel data to image
            if (mGifFrame == null) {
                mGifFrame = new GifFrame(mImage, mDelay, mDispose);
                mCurrentFrame = mGifFrame;
            } else {
                GifFrame f = mGifFrame;
                while (f.mNextFrame != null) {
                    f = f.mNextFrame;
                }
                f.mNextFrame = new GifFrame(mImage, mDelay, mDispose);
            }
            // frames.addElement(new GifFrame(image, delay)); // add image to
            // frame
            // list
            if (mTransparency) {
                mAct[mTransIndex] = save;
            }
            resetFrame();
            mGifAction.parseOk(true, mFrameCount);
        } catch (OutOfMemoryError e) {
            Log.e("GifDecoder", ">>> log  : " + e.toString());
            e.printStackTrace();
        }
    }

    private void readLSD() {
        // logical screen size
        mWidth = readShort();
        mHeight = readShort();
        // packed fields
        int packed = read();
        mGctFlag = (packed & 0x80) != 0; // 1 : global color table flag
        // 2-4 : color resolution
        // 5 : gct sort flag
        mGctSize = 2 << (packed & 7); // 6-8 : gct size
        mBgIndex = read(); // background color index
        mPixelAspect = read(); // pixel aspect ratio
    }

    private void readNetscapeExt() {
        do {
            readBlock();
            if (mBlock[0] == 1) {
                // loop count sub-block
                int b1 = ((int) mBlock[1]) & 0xff;
                int b2 = ((int) mBlock[2]) & 0xff;
                mLoopCount = (b2 << 8) | b1;
            }
        } while ((mBlockSize > 0) && !err());
    }

    private int readShort() {
        // read 16-bit value, LSB first
        return read() | (read() << 8);
    }

    private void resetFrame() {
        mLastDispose = mDispose;
        mLrx = mIx;
        mLry = mIy;
        mLrw = mIw;
        mLrh = mIh;
        mLastImage = mImage;
        mLastBgColor = mBgColor;
        mDispose = 0;
        mTransparency = false;
        mDelay = 0;
        mLct = null;
    }

    /**
     * Skips variable length blocks up to and including next zero length block.
     */
    private void skip() {
        do {
            readBlock();
        } while ((mBlockSize > 0) && !err());
    }

    private void freeFrame() {
        GifFrame fg = mGifFrame;
        while (fg != null) {
            if (fg.mImage != null) {
                fg.mImage.recycle();
            }
            fg.mImage = null;
            fg = null;
            mGifFrame = mGifFrame.mNextFrame;
            fg = mGifFrame;
        }
    }

    private void freeIS() {
        if (mIS != null) {
            try {
                mIS.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            mIS = null;
        }
        mGifData = null;
    }

    private void freeImage() {
        if (mImage != null) {
            mImage.recycle();
            mImage = null;
        }
        if (mLastImage != null) {
            mLastImage.recycle();
            mLastImage = null;
        }
    }
}
