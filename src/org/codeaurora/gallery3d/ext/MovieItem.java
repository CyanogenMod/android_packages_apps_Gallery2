package org.codeaurora.gallery3d.ext;

import android.net.Uri;
import android.provider.MediaStore;

public class MovieItem implements IMovieItem {
    private static final String TAG = "MovieItem";
    private static final boolean LOG = false;

    private Uri mUri;
    private String mMimeType;
    private String mTitle;
    private boolean mError;
    // private int mStereoType;
    private Uri mOriginal;

    private static final int STREO_TYPE_2D = 1;

    public MovieItem(Uri uri, String mimeType, String title, int stereoType) {
        mUri = uri;
        mMimeType = mimeType;
        mTitle = title;
        // mStereoType = stereoType;
        mOriginal = uri;
    }

    public MovieItem(String uri, String mimeType, String title, int stereoType) {
        this(Uri.parse(uri), mimeType, title, stereoType);
    }

    public MovieItem(Uri uri, String mimeType, String title) {
        this(uri, mimeType, title, STREO_TYPE_2D);
    }

    public MovieItem(String uri, String mimeType, String title) {
        this(Uri.parse(uri), mimeType, title);
    }

    @Override
    public Uri getUri() {
        return mUri;
    }

    @Override
    public String getMimeType() {
        return mMimeType;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public boolean getError() {
        return mError;
    }

    // @Override
    // public int getStereoType() {
    // return mStereoType;
    // }

    public void setTitle(String title) {
        mTitle = title;
    }

    @Override
    public void setUri(Uri uri) {
        mUri = uri;
    }

    @Override
    public void setMimeType(String mimeType) {
        mMimeType = mimeType;
    }

    // @Override
    // public void setStereoType(int stereoType) {
    // mStereoType = stereoType;
    // }

    @Override
    public void setError() {
        mError = true;
    }

    @Override
    public Uri getOriginalUri() {
        return mOriginal;
    }

    @Override
    public void setOriginalUri(Uri uri) {
        mOriginal = uri;
    }

    @Override
    public String toString() {
        return new StringBuilder().append("MovieItem(uri=")
                .append(mUri)
                .append(", mime=")
                .append(mMimeType)
                .append(", title=")
                .append(mTitle)
                .append(", error=")
                .append(mError)
                // .append(", support3D=")
                // .append(mStereoType)
                .append(", mOriginal=")
                .append(mOriginal)
                .append(")")
                .toString();
    }
}
