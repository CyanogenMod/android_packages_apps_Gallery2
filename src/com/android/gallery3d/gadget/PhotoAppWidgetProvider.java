/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.gadget;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.gadget.WidgetDatabaseHelper.Entry;
import com.android.gallery3d.onetimeinitializer.GalleryWidgetMigrator;

import java.util.ArrayList;
import java.util.List;

public class PhotoAppWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "WidgetProvider";
    private static List<PhotoUriContentObserver> mPhotoUriObservers = new ArrayList<>();
    private static Handler mContentObserverHandler = new Handler();

    static RemoteViews buildWidget(Context context, int id, Entry entry) {

        switch (entry.type) {
            case WidgetDatabaseHelper.TYPE_ALBUM:
            case WidgetDatabaseHelper.TYPE_SHUFFLE:
                return buildStackWidget(context, id, entry);
            case WidgetDatabaseHelper.TYPE_SINGLE_PHOTO:
                PhotoUriContentObserver photoUriObserver =
                        new PhotoUriContentObserver(context, mContentObserverHandler, entry, id);
                photoUriObserver.setTag(id);
                mPhotoUriObservers.add(photoUriObserver);
                context.getContentResolver().registerContentObserver(Uri.parse(entry.imageUri),
                        false, photoUriObserver);
                return buildFrameWidget(context, id, entry);
        }
        throw new RuntimeException("invalid type - " + entry.type);
    }

    @Override
    public void onUpdate(Context context,
            AppWidgetManager appWidgetManager, int[] appWidgetIds) {

        if (ApiHelper.HAS_REMOTE_VIEWS_SERVICE) {
            // migrate gallery widgets from pre-JB releases to JB due to bucket ID change
            GalleryWidgetMigrator.migrateGalleryWidgets(context);
        }

        WidgetDatabaseHelper helper = new WidgetDatabaseHelper(context);
        try {
            for (int id : appWidgetIds) {
                Entry entry = helper.getEntry(id);
                if (entry != null) {
                    RemoteViews views = buildWidget(context, id, entry);
                    appWidgetManager.updateAppWidget(id, views);
                } else {
                    Log.e(TAG, "cannot load widget: " + id);
                }
            }
        } finally {
            helper.close();
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    @SuppressWarnings("deprecation")
    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    private static RemoteViews buildStackWidget(Context context, int widgetId, Entry entry) {
        RemoteViews views = new RemoteViews(
                context.getPackageName(), R.layout.appwidget_main);

        Intent intent = new Intent(context, WidgetService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        intent.putExtra(WidgetService.EXTRA_WIDGET_TYPE, entry.type);
        intent.putExtra(WidgetService.EXTRA_ALBUM_PATH, entry.albumPath);
        intent.setData(Uri.parse("widget://gallery/" + widgetId));

        // We use the deprecated API for backward compatibility
        // The new API is available in ICE_CREAM_SANDWICH (15)
        views.setRemoteAdapter(widgetId, R.id.appwidget_stack_view, intent);

        views.setEmptyView(R.id.appwidget_stack_view, R.id.appwidget_empty_view);

        Intent clickIntent = new Intent(context, WidgetClickHandler.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        views.setPendingIntentTemplate(R.id.appwidget_stack_view, pendingIntent);

        return views;
    }

    static RemoteViews buildFrameWidget(Context context, int appWidgetId, Entry entry) {
        RemoteViews views = new RemoteViews(
                context.getPackageName(), R.layout.photo_frame);
        try {
            byte[] data = entry.imageData;
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            views.setImageViewBitmap(R.id.photo, bitmap);
        } catch (Throwable t) {
            Log.w(TAG, "cannot load widget image: " + appWidgetId, t);
        }

        if (entry.imageUri != null) {
            try {
                Uri uri = Uri.parse(entry.imageUri);
                Intent clickIntent = new Intent(context, WidgetClickHandler.class)
                        .setData(uri);
                PendingIntent pendingClickIntent = PendingIntent.getActivity(context, 0,
                        clickIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                views.setOnClickPendingIntent(R.id.photo, pendingClickIntent);
            } catch (Throwable t) {
                Log.w(TAG, "cannot load widget uri: " + appWidgetId, t);
            }
        }
        return views;
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // Clean deleted photos out of our database
        WidgetDatabaseHelper helper = new WidgetDatabaseHelper(context);
        for (int appWidgetId : appWidgetIds) {
            PhotoUriContentObserver contentObserver = getContentObserver(appWidgetId);
            if (contentObserver != null) {
                context.getContentResolver().unregisterContentObserver(contentObserver);
                mPhotoUriObservers.remove(contentObserver);
            }
            helper.deleteEntry(appWidgetId);
        }
        helper.close();
    }

    private PhotoUriContentObserver getContentObserver(int appWidgetId) {
        for (PhotoUriContentObserver contentObserver : mPhotoUriObservers) {
            if (appWidgetId == contentObserver.getTag()) {
                return contentObserver;
            }
        }
        return null;
    }

    private static class PhotoUriContentObserver extends ContentObserver {
        private int mId;
        private int mTag;
        private Context mContext;
        private Entry mEntry;

        public void setTag(int tag) {
            this.mTag = tag;
        }

        public int getTag() {
            return mTag;
        }

        public PhotoUriContentObserver(Context context, Handler handler, Entry entry, int id) {
            super(handler);
            mContext = context;
            mEntry = entry;
            mId = id;
        }

        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Uri uri = Uri.parse(mEntry.imageUri);
            Cursor cursor = mContext.getContentResolver().query(uri, null, null,
                    null, "_id ASC LIMIT 1");
            if (cursor != null) {
                if (cursor.getCount() == 0) {
                    RemoteViews views = buildFrameWidget(mContext, mId, mEntry);
                    views.setViewVisibility(R.id.appwidget_empty_photo, View.VISIBLE);
                    views.setViewVisibility(R.id.photo, View.GONE);
                    AppWidgetManager.getInstance(mContext).updateAppWidget(mId, views);
                }
                cursor.close();
            }
        }
    }
}
