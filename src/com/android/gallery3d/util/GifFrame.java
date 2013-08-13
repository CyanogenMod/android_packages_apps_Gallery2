package com.android.gallery3d.util;

import android.graphics.Bitmap;

public class GifFrame {

    public Bitmap image;
    public int delay;
    public GifFrame nextFrame = null;

    public GifFrame(Bitmap im, int del) {
        image = im;
        delay = del;
    }
}