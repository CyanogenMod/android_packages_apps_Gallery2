package com.android.gallery3d.util;

import android.graphics.Bitmap;

public class GifFrame {

    public Bitmap    mImage;
    public int       mDelayInMs;  //in milliseconds
    public int       mDispose;
    public GifFrame  mNextFrame = null;

    public GifFrame(Bitmap bitmap, int delay, int dispose) {
        mImage = bitmap;
        mDelayInMs = delay;
        mDispose = dispose;
    }
}
