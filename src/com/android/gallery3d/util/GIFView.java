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

    private static final String TAG = "GIFView";

    private static boolean isRun = false;
    private static boolean pause = true;

    private GifDecoder gifDecoder = null;
    private Bitmap currentImage = null;

    private int W;
    private int H;

    private DrawThread drawThread = null;

    Uri mUri;
    private Context mContext;

    public GIFView(Context context) {
        super(context);
        mContext = context;
    }

    public boolean setDrawable(Uri uri) {
        if (null == uri) {
            return false;
        }
        isRun = true;
        pause = false;
        mUri = uri;
        int mSize = 0;
        ContentResolver cr = mContext.getContentResolver();
        InputStream input = null;
        try {
            input = cr.openInputStream(uri);
            if (input instanceof FileInputStream) {
                FileInputStream f = (FileInputStream) input;
                mSize = (int) f.getChannel().size();
            } else {
                while (-1 != input.read()) {
                    mSize++;
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "catch exception:" + e);
        }
        if (mSize == 0) {
            return false;
        }

        setGifDecoderImage(input);
        return true;
    }

    private void setGifDecoderImage(InputStream is) {
        if (gifDecoder != null) {
            gifDecoder.free();
            gifDecoder = null;
        }
        gifDecoder = new GifDecoder(is, this);
        gifDecoder.start();
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        W = ViewGifImage.dm.widthPixels;
        H = ViewGifImage.dm.heightPixels;
        if (gifDecoder == null) {
            return;
        }

        if (currentImage == null) {
            currentImage = gifDecoder.getImage();
        }
        if (currentImage == null) {
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

        int imageHeight = currentImage.getHeight();
        int imageWidth = currentImage.getWidth();

        int displayHeight = H;
        int displayWidth = W;

        if (displayWidth < imageWidth) {
            if (displayHeight < imageHeight) {
                if (imageHeight * W > imageWidth * H) {
                    displayWidth = (imageWidth * displayHeight) / imageHeight;
                } else {
                    displayHeight = (imageHeight * displayWidth) / imageWidth;
                }
            } else {
                displayHeight = (imageHeight * displayWidth) / imageWidth;
            }
            dRect = new Rect((W - displayWidth) / 2, (H - displayHeight) / 2,
                    (W + displayWidth) / 2, (H + displayHeight) / 2);
            canvas.drawBitmap(currentImage, sRect, dRect, null);
        } else if (displayHeight < imageHeight) {
            displayWidth = (imageWidth * displayHeight) / imageHeight;
            dRect = new Rect((W - displayWidth) / 2, 0,
                    (W + displayWidth) / 2, displayHeight);
            canvas.drawBitmap(currentImage, sRect, dRect, null);
        } else {
            canvas.drawBitmap(currentImage, (W - imageWidth) / 2, (H - imageHeight) / 2, null);
        }
        canvas.restoreToCount(saveCount);
    }

    public void parseOk(boolean parseStatus, int frameIndex) {
        if (parseStatus) {
            if (gifDecoder != null && frameIndex == -1
                    && gifDecoder.getFrameCount() > 1) {
                if (drawThread == null) {
                    drawThread = new DrawThread();
                } else {
                    drawThread = null;
                    drawThread = new DrawThread();
                }
                drawThread.start();
            }
        } else {
            Log.e(TAG, "parse error");
        }
    }

    private Handler redrawHandler = new Handler() {
        public void handleMessage(Message msg) {
            invalidate();
        }
    };

    private class DrawThread extends Thread {
        public void run() {
            if (gifDecoder == null) {
                return;
            }

            while (isRun) {
                if (pause == false) {
                    if (!isShown()) {
                        isRun = false;
                        pause = true;
                        break;
                    }
                    GifFrame frame = gifDecoder.next();
                    currentImage = frame.image;
                    long sp = frame.delay;
                    if (sp == 0) {
                        sp = 200;
                    }
                    if (redrawHandler != null) {
                        Message msg = redrawHandler.obtainMessage();
                        redrawHandler.sendMessage(msg);
                        try {
                            Thread.sleep(sp);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "catch exception:" + e);
                        }
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            isRun = true;
            pause = false;
        }
    }

    public void freeMemory() {
        isRun = false;
        pause = true;
        if (drawThread != null) {
            drawThread = null;
        }
        if (gifDecoder != null) {
            gifDecoder.free();
            gifDecoder = null;
        }
    }
}