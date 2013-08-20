/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.print.pdf.PdfDocument;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.imageshow.MasterImage;

import java.io.FileDescriptor;
import java.io.FileOutputStream;

public class PrintJob {
    private final static int MAX_PRINT_SIZE = 2048;

    public static void printBitmap(Context context, final String jobName, final Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        PrintManager printManager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
        android.print.PrintJob printJob = printManager.print(jobName,
                new PrintDocumentAdapter() {
                    private Rect mPageRect;
                    private Matrix mPrintMatrix;
                    private float mDensity;
                    @Override
                    public void onLayout(PrintAttributes oldPrintAttributes,
                                         PrintAttributes newPrintAttributes,
                                         CancellationSignal cancellationSignal,
                                         LayoutResultCallback layoutResultCallback, Bundle bundle) {

                        mDensity = Math.max(newPrintAttributes.getResolution().getHorizontalDpi(),
                                newPrintAttributes.getResolution().getVerticalDpi());

                        float MILS_PER_INCH = 1000f;

                        int pageWidth = (int) (mDensity
                                * newPrintAttributes.getMediaSize().getWidthMils() / MILS_PER_INCH);
                        int pageHeight = (int) (mDensity
                                * newPrintAttributes.getMediaSize().getWidthMils() / MILS_PER_INCH);

                        mPageRect = new Rect(0, 0, pageWidth, pageHeight);
                        if (newPrintAttributes.getOrientation()
                                == PrintAttributes.ORIENTATION_LANDSCAPE) {
                            mPageRect = new Rect(0, 0, pageHeight, pageWidth);
                        }

                        PrintDocumentInfo info = new PrintDocumentInfo.Builder(jobName)
                                .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
                                .setPageCount(1)
                                .create();
                        layoutResultCallback.onLayoutFinished(info, false);
                    }

                    @Override
                    public void onWrite(PageRange[] pageRanges, FileDescriptor fileDescriptor,
                                        CancellationSignal cancellationSignal,
                                        WriteResultCallback writeResultCallback) {
                        PdfDocument mPdfDocument = PdfDocument.open();
                        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                                mPageRect, 1, (int) mDensity).create();
                        PdfDocument.Page page = mPdfDocument.startPage(pageInfo);
                        mPrintMatrix = new Matrix();
                        mPrintMatrix.setRectToRect(
                                new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                                new RectF(mPageRect),
                                Matrix.ScaleToFit.CENTER);
                        page.getCanvas().drawBitmap(bitmap, mPrintMatrix, null);
                        mPdfDocument.finishPage(page);
                        mPdfDocument.writeTo(new FileOutputStream(fileDescriptor));
                        mPdfDocument.close();
                        writeResultCallback.onWriteFinished(
                                new PageRange[] { PageRange.ALL_PAGES });
                    }
                }, new PrintAttributes.Builder().create());

    }

    public static void printBitmapAtUri(Context context, String imagePrint, Uri uri) {
        // TODO: load full size images. For now, it's better to constrain ourselves.
        Bitmap bitmap = ImageLoader.loadConstrainedBitmap(uri, context, MAX_PRINT_SIZE, null, false);
        printBitmap(context, imagePrint, bitmap);
    }
}
