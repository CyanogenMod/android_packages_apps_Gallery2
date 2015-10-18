package org.codeaurora.gallery3d.video;

import android.view.Menu;
import android.view.MenuItem;

import com.android.gallery3d.R;
import org.codeaurora.gallery3d.ext.MovieUtils;

public class LoopVideoHooker extends MovieHooker {

    private static final String TAG = "LoopVideoHooker";
    private static final boolean LOG = false;
    private static final int MENU_LOOP = 1;

    private MenuItem mMenuLoopButton;

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        mMenuLoopButton = menu.add(0, getMenuActivityId(MENU_LOOP), 0, R.string.loop);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        updateLoop();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (getMenuOriginalId(item.getItemId())) {
            case MENU_LOOP:
                getPlayer().setLoop(!getPlayer().getLoop());
                return true;
            default:
                return false;
        }
    }

    private void updateLoop() {
        if (mMenuLoopButton != null) {
            if (MovieUtils.isLocalFile(getMovieItem().getUri(), getMovieItem().getMimeType())) {
                mMenuLoopButton.setVisible(true);
            } else {
                mMenuLoopButton.setVisible(false);
            }
            final boolean newLoop = getPlayer().getLoop();
            if (newLoop) {
                mMenuLoopButton.setTitle(R.string.single);
                mMenuLoopButton.setIcon(R.drawable.ic_menu_unloop);
            } else {
                mMenuLoopButton.setTitle(R.string.loop);
                mMenuLoopButton.setIcon(R.drawable.ic_menu_loop);
            }
        }
    }
}
