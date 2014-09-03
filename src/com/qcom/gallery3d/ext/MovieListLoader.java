package com.qcom.gallery3d.ext;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
/**
 * Movie list loader class. It will load videos from MediaProvider database.
 * If MoviePlayer starting activity doesn't set any thing, default OrderBy will be used.
 * Default OrderBy: MediaStore.Video.Media.DATE_TAKEN + " DESC, " + MediaStore.Video.Media._ID + " DESC ";
 */
public class MovieListLoader implements IMovieListLoader {  
    private static final String TAG = "MovieListLoader";
    private static final boolean LOG = true;
    
    private MovieListFetcherTask mListTask;
    
    @Override
    public void fillVideoList(Context context, Intent intent, LoaderListener l, IMovieItem item) {
        boolean fetechAll = false;
        if (intent.hasExtra(EXTRA_ALL_VIDEO_FOLDER)) {
            fetechAll = intent.getBooleanExtra(EXTRA_ALL_VIDEO_FOLDER, false);
        }
        //default order by
        String orderBy = MediaStore.Video.Media.DATE_TAKEN + " DESC, " + MediaStore.Video.Media._ID + " DESC ";
        if (intent.hasExtra(EXTRA_ORDERBY)) {
            orderBy = intent.getStringExtra(EXTRA_ORDERBY);
        }
        cancelList();
        mListTask = new MovieListFetcherTask(context, fetechAll, l, orderBy);
        mListTask.execute(item);
        if (LOG) {
            QcomLog.v(TAG, "fillVideoList() fetechAll=" + fetechAll + ", orderBy=" + orderBy);
        }
    }
    
    @Override
    public boolean isEnabledVideoList(Intent intent) {
        boolean enable = true;
        if (intent != null && intent.hasExtra(EXTRA_ENABLE_VIDEO_LIST)) {
            enable = intent.getBooleanExtra(EXTRA_ENABLE_VIDEO_LIST, true);
        }
        if (LOG) {
            QcomLog.v(TAG, "isEnabledVideoList() return " + enable);
        }
        return enable;
    }
    
    @Override
    public void cancelList() {
        if (mListTask != null) {
            mListTask.cancel(true);
        }
    }

    private class MovieListFetcherTask extends AsyncTask<IMovieItem, Void, IMovieList> {
        private static final String TAG = "MovieListFetcherTask";
        private static final boolean LOG = true;
        
        // TODO comments by sunlei
//        public static final String COLUMN_STEREO_TYPE = MediaStore.Video.Media.STEREO_TYPE;
//        public static final String COLUMN_STEREO_TYPE = "STEREO_TYPE";
        
        private final ContentResolver mCr;
        private final LoaderListener mFetecherListener;
        private final boolean mFetechAll;
        private final String mOrderBy;
        
        public MovieListFetcherTask(Context context, boolean fetechAll, LoaderListener l, String orderBy) {
            mCr = context.getContentResolver();
            mFetecherListener = l;
            mFetechAll = fetechAll;
            mOrderBy = orderBy;
            if (LOG) {
                QcomLog.v(TAG, "MovieListFetcherTask() fetechAll=" + fetechAll + ", orderBy=" + orderBy);
            }
        }
        
        @Override
        protected void onPostExecute(IMovieList params) {
            if (LOG) {
                QcomLog.v(TAG, "onPostExecute() isCancelled()=" + isCancelled());
            }
            if (isCancelled()) {
                return;
            }
            if (mFetecherListener != null) {
                mFetecherListener.onListLoaded(params);
            }
        }
        
        @Override
        protected IMovieList doInBackground(IMovieItem... params) {
            if (LOG) {
                QcomLog.v(TAG, "doInBackground() begin");
            }
            if (params[0] == null) {
                return null;
            }
            IMovieList movieList = null;
            Uri uri = params[0].getUri();
            String mime = params[0].getMimeType();
            if (mFetechAll) { //get all list
                if (MovieUtils.isLocalFile(uri, mime)) {
                    String uristr = String.valueOf(uri);
                    if (uristr.toLowerCase().startsWith("content://media")) {
                        //from gallery, gallery3D, videoplayer
                        long curId = Long.parseLong(uri.getPathSegments().get(3));
                        movieList = fillUriList(null, null, curId, params[0]);
                    }
                }
            } else { //get current list
                if (MovieUtils.isLocalFile(uri, mime)) {
                    String uristr = String.valueOf(uri);
                    if (uristr.toLowerCase().startsWith("content://media")) {
                        Cursor cursor = mCr.query(uri,
                                new String[]{MediaStore.Video.Media.BUCKET_ID},
                                null, null, null);
                        long bucketId = -1;
                        if (cursor != null) {
                            if (cursor.moveToFirst()) {
                                bucketId = cursor.getLong(0);
                            }
                            cursor.close();
                        }
                        long curId = Long.parseLong(uri.getPathSegments().get(3));
                        movieList = fillUriList(MediaStore.Video.Media.BUCKET_ID + "=? ",
                                new String[]{String.valueOf(bucketId)}, curId, params[0]);
                    } else if (uristr.toLowerCase().startsWith("file://")) {
                        String data = Uri.decode(uri.toString());
                        data = data.replaceAll("'", "''");
                        String where = "_data LIKE '%" + data.replaceFirst("file:///", "") + "'";
                        Cursor cursor = mCr.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                new String[]{"_id", MediaStore.Video.Media.BUCKET_ID},
                                where, null, null);
                        long bucketId = -1;
                        long curId = -1;
                        if (cursor != null) {
                            if (cursor.moveToFirst()) {
                                curId = cursor.getLong(0);
                                bucketId = cursor.getLong(1);
                            }
                            cursor.close();
                        }
                        movieList = fillUriList(MediaStore.Video.Media.BUCKET_ID + "=? ",
                                new String[]{String.valueOf(bucketId)}, curId, params[0]);
                    }
                }
            }
            if (LOG) {
                QcomLog.v(TAG, "doInBackground() done return " + movieList);
            }
            return movieList;
        }
        
        private IMovieList fillUriList(String where, String[] whereArgs, long curId, IMovieItem current) {
            IMovieList movieList = null;
            Cursor cursor = null;
            try {
                cursor = mCr.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        new String[]{"_id", "mime_type", OpenableColumns.DISPLAY_NAME},
                        where,
                        whereArgs,
                        mOrderBy);
                boolean find = false;
                if (cursor != null && cursor.getCount() > 0) {
                    movieList = new MovieList();
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(0);
                        if (!find && id == curId) {
                            find = true;
                            movieList.add(current);
                            continue;
                        }
                        Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                        String mimeType = cursor.getString(1);
                        String title = cursor.getString(2);

                        movieList.add(new MovieItem(uri, mimeType, title));
                    }
                }
            } catch (final SQLiteException e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            if (LOG) {
                QcomLog.v(TAG, "fillUriList() cursor=" + cursor + ", return " + movieList);
            }
            return movieList;
        }
    }
}