package org.codeaurora.gallery3d.video;

import android.util.Log;

import org.codeaurora.gallery3d.ext.ActivityHooker;
import org.codeaurora.gallery3d.ext.IMovieItem;
import org.codeaurora.gallery3d.ext.IMoviePlayer;

public class MovieHooker extends ActivityHooker {

    private static final String TAG = "MovieHooker";
    private static final boolean LOG = false;
    private IMovieItem mMovieItem;
    private IMoviePlayer mPlayer;

    @Override
    public void setParameter(final String key, final Object value) {
        super.setParameter(key, value);
        if (LOG) {
            Log.v(TAG, "setParameter(" + key + ", " + value + ")");
        }
        if (value instanceof IMovieItem) {
            mMovieItem = (IMovieItem) value;
            onMovieItemChanged(mMovieItem);
        } else if (value instanceof IMoviePlayer) {
            mPlayer = (IMoviePlayer) value;
            onMoviePlayerChanged(mPlayer);
        }
    }

    public IMovieItem getMovieItem() {
        return mMovieItem;
    }

    public IMoviePlayer getPlayer() {
        return mPlayer;
    }

    public void onMovieItemChanged(final IMovieItem item) {
    }

    public void onMoviePlayerChanged(final IMoviePlayer player) {
    }
}
