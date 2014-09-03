package com.qcom.gallery3d.ext;

import android.content.Context;
import android.content.Intent;

public interface IMovieListLoader {
    /**
     * Load all video list or not.[boolean]
     * "yes" means load all videos in all storages.
     * "false" means load videos located in current video's folder.
     */
    String EXTRA_ALL_VIDEO_FOLDER = "qcom.intent.extra.ALL_VIDEO_FOLDER";
    /**
     * Video list order by column name.[String]
     */
    String EXTRA_ORDERBY = "qcom.intent.extra.VIDEO_LIST_ORDERBY";
    /**
     * Enable video list or not.[boolean]
     */
    String EXTRA_ENABLE_VIDEO_LIST = "qcom.intent.extra.ENABLE_VIDEO_LIST";
    /**
     * Loader listener interface
     */
    public interface LoaderListener {
        /**
         * Will be called after movie list loaded.
         * @param movieList
         */
        void onListLoaded(IMovieList movieList);
    }
    /**
     * Build the movie list from current item.
     * @param context
     * @param intent
     * @param l
     * @param item
     */
    void fillVideoList(Context context, Intent intent, LoaderListener l, IMovieItem item);
    /**
     * enable video list or not.
     * @param intent
     * @return
     */
    boolean isEnabledVideoList(Intent intent);
    /**
     * Cancel current loading process.
     */
    void cancelList();

}
