package org.codeaurora.gallery3d.video;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.android.gallery3d.R;
import org.codeaurora.gallery3d.ext.IMovieItem;
import org.codeaurora.gallery3d.ext.IMovieList;
import org.codeaurora.gallery3d.ext.IMovieListLoader;
import org.codeaurora.gallery3d.ext.IMovieListLoader.LoaderListener;
import org.codeaurora.gallery3d.ext.MovieListLoader;

public class MovieListHooker extends MovieHooker implements LoaderListener {
    private static final String TAG = "MovieListHooker";
    private static final boolean LOG = false;
    
    private static final int MENU_NEXT = 1;
    private static final int MENU_PREVIOUS = 2;
    
    private MenuItem mMenuNext;
    private MenuItem mMenuPrevious;
    
    private IMovieListLoader mMovieLoader;
    private IMovieList mMovieList;
    
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMovieLoader = new MovieListLoader();
        mMovieLoader.fillVideoList(getContext(), getIntent(), this, getMovieItem());
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        mMovieLoader.cancelList();
    }
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        if (mMovieList != null) { //list should be filled
            if (mMovieLoader != null && mMovieLoader.isEnabledVideoList(getIntent())) {
                mMenuPrevious = menu.add(0, getMenuActivityId(MENU_PREVIOUS), 0, R.string.previous);
                mMenuNext = menu.add(0, getMenuActivityId(MENU_NEXT), 0, R.string.next);
            }
        }
        return true;
    }
    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        updatePrevNext();
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        super.onOptionsItemSelected(item);
        switch(getMenuOriginalId(item.getItemId())) {
        case MENU_PREVIOUS:
            if (mMovieList == null) {
                return false;
            }
            getPlayer().startNextVideo(mMovieList.getPrevious(getMovieItem()));
            return true;
        case MENU_NEXT:
            if (mMovieList == null) {
                return false;
            }
            getPlayer().startNextVideo(mMovieList.getNext(getMovieItem()));
            return true;
        default:
            return false;
        }
    }
    
    @Override
    public void onMovieItemChanged(final IMovieItem item) {
        super.onMovieItemChanged(item);
        updatePrevNext();
    }
    
    private void updatePrevNext() {
        if (LOG) {
            Log.v(TAG, "updatePrevNext()");
        }
        if (mMovieList != null && mMenuPrevious != null && mMenuNext != null) {
            if (mMovieList.isFirst(getMovieItem()) && mMovieList.isLast(getMovieItem())) { //only one movie
                mMenuNext.setVisible(false);
                mMenuPrevious.setVisible(false);
            } else {
                mMenuNext.setVisible(true);
                mMenuPrevious.setVisible(true);
            }
            if (mMovieList.isFirst(getMovieItem())) {
                mMenuPrevious.setEnabled(false);
            } else {
                mMenuPrevious.setEnabled(true);
            }
            if (mMovieList.isLast(getMovieItem())) {
                mMenuNext.setEnabled(false);
            } else {
                mMenuNext.setEnabled(true);
            }
        }
    }
    
    @Override
    public void onListLoaded(final IMovieList movieList) {
        mMovieList = movieList;
        getContext().invalidateOptionsMenu();
        if (LOG) {
            Log.v(TAG, "onListLoaded() " + (mMovieList != null ? mMovieList.size() : "null"));
        }
    }
}
