package org.codeaurora.gallery3d.video;

import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;

import com.android.gallery3d.R;
import com.android.gallery3d.app.MovieActivity;
import org.codeaurora.gallery3d.ext.ActivityHooker;
import org.codeaurora.gallery3d.video.VideoSettingsActivity;

public class StepOptionSettingsHooker extends ActivityHooker {
    private static final int MENU_STEP_OPTION_SETTING = 1;
    private MenuItem mMenuStepOption;
    
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        mMenuStepOption = menu.add(0, getMenuActivityId(MENU_STEP_OPTION_SETTING), 0, R.string.settings);
        return true;
    }
    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        super.onOptionsItemSelected(item);
        switch(getMenuOriginalId(item.getItemId())) {
        case MENU_STEP_OPTION_SETTING:
            //start activity
            Intent mIntent = new Intent();
            mIntent.setClass(getContext(), VideoSettingsActivity.class);
            getContext().startActivity(mIntent);
            return true;
        default:
            return false;
        }
    }
}