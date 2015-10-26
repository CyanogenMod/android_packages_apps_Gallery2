package org.codeaurora.gallery3d.ext;

import android.net.Uri;

/**
 * Movie info class
 */
public interface IMovieItem {
    /**
     * @return movie Uri, it's may be not the original Uri.
     */
    Uri getUri();

    /**
     * @return MIME type of video
     */
    String getMimeType();

    /**
     * @return title of video
     */
    String getTitle();

    /**
     * @return whether error occured or not.
     */
    boolean getError();

    /**
     * set title of video
     *
     * @param title
     */
    void setTitle(String title);

    /**
     * set video Uri
     *
     * @param uri
     */
    void setUri(Uri uri);

    /**
     * Set MIME type of video
     *
     * @param mimeType
     */
    void setMimeType(String mimeType);

    /**
     * Set error occured flag
     */
    void setError();

    /**
     * @return return original Uri of video.
     */
    Uri getOriginalUri();

    /**
     * Set video original Uri.
     *
     * @param uri
     */
    void setOriginalUri(Uri uri);
}
