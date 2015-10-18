package com.android.gallery3d.util;

import com.android.gallery3d.R;

import android.content.Context;
import android.content.ContentResolver;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;

public class GIFView extends ImageView implements GifAction {

    private static final String  TAG            = "GIFView";
    private static final float   SCALE_LIMIT    = 4;
    private static final long    FRAME_DELAY    = 200; //milliseconds

    private GifDecoder           mGifDecoder    = null;
    private Bitmap               mCurrentImage  = null;
    private DrawThread           mDrawThread    = null;

    private Uri                  mUri;
    private Context              mContext;

    public GIFView(Context context) {
        super(context);
        mContext = context;
    }

    public boolean setDrawable(Uri uri) {
        if (null == uri) {
            return false;
        }
        mUri = uri;

        InputStream is = getInputStream(uri);
        if (is == null || (getFileSize (is) == 0)) {
            return false;
        }
        startDecode(is);
        return true;
    }

    private int getFileSize (InputStream is) {
        if(is == null) return 0;

        int size = 0;
        try {
            if (is instanceof FileInputStream) {
                FileInputStream f = (FileInputStream) is;
                size = (int) f.getChannel().size();
            } else {
                while (-1 != is.read()) {
                    size++;
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "catch exception:" + e);
        }

        return size;

    }

    private InputStream getInputStream (Uri uri) {
        ContentResolver cr = mContext.getContentResolver();
        InputStream input = null;
        try {
            input = cr.openInputStream(uri);
        } catch (IOException e) {
            Log.e(TAG, "catch exception:" + e);
        }
        return input;
    }

    private void startDecode(InputStream is) {
        freeGifDecoder();
        mGifDecoder = new GifDecoder(is, this);
        mGifDecoder.start();
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mGifDecoder == null) {
            return;
        }

        if (mCurrentImage == null) {
            mCurrentImage = mGifDecoder.getImage();
        }
        if (mCurrentImage == null) {
            // if this gif can not be displayed, just try to show it as jpg by parsing mUri
            setImageURI(mUri);
            return;
        }
        setImageURI(null);
        int saveCount = canvas.getSaveCount();
        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());
        Rect sRect = null;
        Rect dRect = null;

        int imageHeight = mCurrentImage.getHeight();
        int imageWidth = mCurrentImage.getWidth();

        int displayHeight = ViewGifImage.mDM.heightPixels;
        int displayWidth = ViewGifImage.mDM.widthPixels;

        int width, height;
        if (imageWidth >= displayWidth || imageHeight >= displayHeight) {
            // scale-down the image
            if (imageWidth * displayHeight > displayWidth * imageHeight) {
                width = displayWidth;
                height = (imageHeight * width) / imageWidth;
            } else {
                height = displayHeight;
                width = (imageWidth * height) / imageHeight;
            }
        } else {
            // scale-up the image
            float scale = Math.min(SCALE_LIMIT, Math.min(displayWidth / (float) imageWidth,
                    displayHeight / (float) imageHeight));
            width = (int) (imageWidth * scale);
            height = (int) (imageHeight * scale);
        }
        dRect = new Rect((displayWidth - width) / 2, (displayHeight - height) / 2,
                (displayWidth + width) / 2, (displayHeight + height) / 2);
        canvas.drawBitmap(mCurrentImage, sRect, dRect, null);
        canvas.restoreToCount(saveCount);
    }

    public void parseOk(boolean parseStatus, int frameIndex) {
        if (parseStatus) {
            //indicates the start of a new GIF
            if (mGifDecoder != null && frameIndex == -1
                    && mGifDecoder.getFrameCount() > 1) {
                if (mDrawThread != null) {
                    mDrawThread = null;
                }
                mDrawThread = new DrawThread();
                mDrawThread.start();
            }
        } else {
            Log.e(TAG, "parse error");
        }
    }

    private Handler mRedrawHandler = new Handler() {
        public void handleMessage(Message msg) {
            invalidate();
        }
    };

    private class DrawThread extends Thread {
        public void run() {
            if (mGifDecoder == null) {
                return;
            }

            while (true) {
                if (!isShown() || mRedrawHandler == null) {
                    break;
                }
                if (mGifDecoder == null) {
                    return;
                }
                GifFrame frame = mGifDecoder.next();
                mCurrentImage = frame.mImage;

                Message msg = mRedrawHandler.obtainMessage();
                mRedrawHandler.sendMessage(msg);
                try {
                    Thread.sleep(getDelay(frame));
                } catch (InterruptedException e) {
                    Log.e(TAG, "catch exception:" + e);
                }
            }
        }

    }

    private long getDelay (GifFrame frame) {
        //in milliseconds
        return frame.mDelayInMs == 0 ? FRAME_DELAY : frame.mDelayInMs;
    }

    private void freeGifDecoder () {
        if (mGifDecoder != null) {
            mGifDecoder.free();
            mGifDecoder = null;
        }

    }

    public void freeMemory() {
        if (mDrawThread != null) {
            mDrawThread = null;
        }
        freeGifDecoder();
    }
}
