package org.codeaurora.gallery3d.video;

import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;

import com.android.gallery3d.R;
import org.codeaurora.gallery3d.ext.MovieUtils;

public class BookmarkHooker extends MovieHooker {
    private static final String TAG = "BookmarkHooker";
    private static final boolean LOG = false;

    private static final String ACTION_BOOKMARK = "org.codeaurora.bookmark.VIEW";
    private static final int MENU_BOOKMARK_ADD = 1;
    private static final int MENU_BOOKMARK_DISPLAY = 2;
    private MenuItem mMenuBookmarks;
    private MenuItem mMenuBookmarkAdd;

    public static final String KEY_LOGO_BITMAP = "logo-bitmap";

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        mMenuBookmarkAdd = menu.add(0, getMenuActivityId(MENU_BOOKMARK_ADD), 0,
                R.string.bookmark_add);
        mMenuBookmarks = menu.add(0, getMenuActivityId(MENU_BOOKMARK_DISPLAY), 0,
                R.string.bookmark_display);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (MovieUtils.isLocalFile(getMovieItem().getUri(), getMovieItem().getMimeType())) {
            if (mMenuBookmarkAdd != null) {
                mMenuBookmarkAdd.setVisible(false);
            }
            if (mMenuBookmarks != null) {
                mMenuBookmarks.setVisible(false);
            }
        } else {
            if (mMenuBookmarkAdd != null) {
                mMenuBookmarkAdd.setVisible(true);
            }
            if (mMenuBookmarks != null) {
                mMenuBookmarks.setVisible(true);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (getMenuOriginalId(item.getItemId())) {
            case MENU_BOOKMARK_ADD:
                getPlayer().addBookmark();
                return true;
            case MENU_BOOKMARK_DISPLAY:
                gotoBookmark();
                return true;
            default:
                return false;
        }
    }

    private void gotoBookmark() {
        final Intent intent = new Intent(ACTION_BOOKMARK);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
        intent.putExtra(KEY_LOGO_BITMAP, getIntent().getParcelableExtra(KEY_LOGO_BITMAP));
        getContext().startActivity(intent);
    }
}
