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
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.print.pdf.PdfDocument.Page;
import android.print.pdf.PrintedPdfDocument;

import com.android.gallery3d.filtershow.cache.ImageLoader;

import java.io.FileOutputStream;
import java.io.IOException;

public class PrintJob {
    private final static int MAX_PRINT_SIZE = 2048;

    public static void printBitmap(final Context context, final String jobName,
            final Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        PrintManager printManager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
        printManager.print(jobName,
                new PrintDocumentAdapter() {
                    private PrintAttributes mAttributes;

                    @Override
                    public void onLayout(PrintAttributes oldPrintAttributes,
                                         PrintAttributes newPrintAttributes,
                                         CancellationSignal cancellationSignal,
                                         LayoutResultCallback layoutResultCallback,
                                         Bundle bundle) {

                        mAttributes = newPrintAttributes;

                        PrintDocumentInfo info = new PrintDocumentInfo.Builder(jobName)
                                .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
                                .setPageCount(1)
                                .build();

                        layoutResultCallback.onLayoutFinished(info, false);
                    }

                    @Override
                    public void onWrite(PageRange[] pageRanges, ParcelFileDescriptor fileDescriptor,
                                        CancellationSignal cancellationSignal,
                                        WriteResultCallback writeResultCallback) {
                        try {
                            PrintedPdfDocument pdfDocument = PrintedPdfDocument.open(context,
                                    mAttributes);
                            Page page = pdfDocument.startPage(1);

                            RectF content = new RectF(page.getInfo().getContentSize());
                            Matrix matrix = new Matrix();

                            // Compute and apply scale to fill the page.
                            float scale = Math.max(content.width() / bitmap.getWidth(),
                                            content.height() / bitmap.getHeight());
                            matrix.postScale(scale, scale);

                            // Center the content.
                            final float translateX = (content.width()
                                    - bitmap.getWidth() * scale) / 2;
                            final float translateY = (content.height()
                                    - bitmap.getHeight() * scale) / 2;
                            matrix.postTranslate(translateX, translateY);

                            // Draw the bitmap.
                            page.getCanvas().drawBitmap(bitmap, matrix, null);

                            // Write the document.
                            pdfDocument.finishPage(page);
                            pdfDocument.writeTo(new FileOutputStream(
                                    fileDescriptor.getFileDescriptor()));
                            pdfDocument.close();

                            // Done.
                            writeResultCallback.onWriteFinished(
                                    new PageRange[] { PageRange.ALL_PAGES });
                        } finally {
                            if (fileDescriptor != null) {
                                try {
                                    fileDescriptor.close();
                                } catch (IOException ioe) {
                                    /* ignore */
                                }
                            }
                        }
                    }
                }, null);
    }

    public static void printBitmapAtUri(Context context, String imagePrint, Uri uri) {
        // TODO: load full size images. For now, it's better to constrain ourselves.
        Bitmap bitmap = ImageLoader.loadConstrainedBitmap(uri, context, MAX_PRINT_SIZE, null, false);
        printBitmap(context, imagePrint, bitmap);
    }
}
