package org.codeaurora.gallery3d.ext;

import android.util.Log;

import java.util.ArrayList;

public class MovieList implements IMovieList {
    private static final String TAG = "MovieList";
    private static final boolean LOG = false;
    
    private final ArrayList<IMovieItem> mItems = new ArrayList<IMovieItem>();
    private static final int UNKNOWN = -1;
    
    @Override
    public void add(IMovieItem item) {
        if (LOG) {
            Log.v(TAG, "add(" + item + ")");
        }
        mItems.add(item);
    }
    
    @Override
    public int index(IMovieItem item) {
        int find = UNKNOWN;
        int size = mItems.size();
        for (int i = 0; i < size; i++) {
            if (item == mItems.get(i)) {
                find = i;
                break;
            }
        }
        if (LOG) {
            Log.v(TAG, "index(" + item + ") return " + find);
        }
        return find;
    }
    
    @Override
    public int size() {
        return mItems.size();
    }
    
    @Override
    public IMovieItem getNext(IMovieItem item) {
        IMovieItem next = null;
        int find = index(item);
        if (find >= 0 && find < size() - 1) {
            next = mItems.get(++find);
        }
        return next;
    }
    
    @Override
    public IMovieItem getPrevious(IMovieItem item) {
        IMovieItem prev = null;
        int find = index(item);
        if (find > 0 && find < size()) {
            prev = mItems.get(--find);
        }
        return prev;
    }

    @Override
    public boolean isFirst(IMovieItem item) {
        return getPrevious(item) == null;
    }

    @Override
    public boolean isLast(IMovieItem item) {
        return getNext(item) == null;
    }
}
