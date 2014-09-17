package org.codeaurora.gallery3d.video;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;


import java.io.File;

public class MovieTitleHelper {
    private static final String TAG = "MovieTitleHelper";
    private static final boolean LOG = false;

    public static String getTitleFromMediaData(final Context context, final Uri uri) {
        String title = null;
        Cursor cursor = null;
        try {
            String data = Uri.decode(uri.toString());
            data = data.replaceAll("'", "''");
            final String where = "_data LIKE '%" + data.replaceFirst("file:///", "") + "'";
            cursor = context.getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    new String[] {
                        OpenableColumns.DISPLAY_NAME
                    }, where, null, null);
            if (LOG) {
                Log.v(
                        TAG,
                        "setInfoFromMediaData() cursor="
                                + (cursor == null ? "null" : cursor.getCount()));
            }
            if (cursor != null && cursor.moveToFirst()) {
                title = cursor.getString(0);
            }
        } catch (final SQLiteException ex) {
            ex.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (LOG) {
            Log.v(TAG, "setInfoFromMediaData() return " + title);
        }
        return title;
    }

    public static String getTitleFromDisplayName(final Context context, final Uri uri) {
        String title = null;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[] {
                        OpenableColumns.DISPLAY_NAME
                    }, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                title = cursor.getString(0);
            }
        } catch (final SQLiteException ex) {
            ex.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (LOG) {
            Log.v(TAG, "getTitleFromDisplayName() return " + title);
        }
        return title;
    }

    public static String getTitleFromUri(final Uri uri) {
        final String title = Uri.decode(uri.getLastPathSegment());
        if (LOG) {
            Log.v(TAG, "getTitleFromUri() return " + title);
        }
        return title;
    }

    public static String getTitleFromData(final Context context, final Uri uri) {
        String title = null;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[] {
                        "_data"
                    }, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                final File file = new File(cursor.getString(0));
                title = file.getName();
            }
        } catch (final SQLiteException ex) {
            ex.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (LOG) {
            Log.v(TAG, "getTitleFromData() return " + title);
        }
        return title;
    }
}
