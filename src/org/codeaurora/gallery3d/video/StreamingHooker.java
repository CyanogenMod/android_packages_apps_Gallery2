package org.codeaurora.gallery3d.video;

import android.content.Intent;
import android.net.Uri;
import android.provider.Browser;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.android.gallery3d.R;
import org.codeaurora.gallery3d.ext.MovieUtils;

public class StreamingHooker extends MovieHooker {
    private static final String TAG = "StreamingHooker";
    private static final boolean LOG = true;

    private static final String ACTION_STREAMING = "org.codeaurora.settings.streaming";
    private static final int MENU_INPUT_URL = 1;
    private static final int MENU_SETTINGS = 2;
    private static final int MENU_DETAIL = 3;

    public static final String KEY_LOGO_BITMAP = "logo-bitmap";

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        // when in rtsp streaming type, generally it only has one uri.
        menu.add(0, getMenuActivityId(MENU_INPUT_URL), 0, R.string.input_url);
        menu.add(0, getMenuActivityId(MENU_SETTINGS), 0, R.string.streaming_settings);
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
        switch (getMenuOriginalId(item.getItemId())) {
            case MENU_INPUT_URL:
                gotoInputUrl();
                return true;
            case MENU_SETTINGS:
                gotoSettings();
                return true;
            default:
                return false;
        }
    }

    private void gotoInputUrl() {
        final String appName = getClass().getName();
        final Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("about:blank"));
        intent.putExtra("inputUrl", true);
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, appName);
        getContext().startActivity(intent);
        if (LOG) {
            Log.v(TAG, "gotoInputUrl() appName=" + appName);
        }
    }

    private void gotoSettings() {
        final Intent intent = new Intent(ACTION_STREAMING);
        // intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
        // | Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
        // intent.putExtra(KEY_LOGO_BITMAP,
        // getIntent().getParcelableExtra(KEY_LOGO_BITMAP));
        getContext().startActivity(intent);
        if (LOG) {
            Log.v(TAG, "gotoInputUrl()");
        }
    }
}
