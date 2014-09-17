package org.codeaurora.gallery3d.video;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.android.gallery3d.R;

public class StereoAudioHooker extends MovieHooker {
    private static final String TAG = "StereoAudioHooker";
    private static final boolean LOG = false;

    private static final int MENU_STEREO_AUDIO = 1;
    private MenuItem mMenuStereoAudio;

    private static final String KEY_STEREO = "EnableStereoOutput";
    private boolean mSystemStereoAudio;
    private boolean mCurrentStereoAudio;
    private boolean mIsInitedStereoAudio;
    private AudioManager mAudioManager;

    @Override
    public void onStart() {
        super.onStart();
        enableStereoAudio();
    }

    @Override
    public void onStop() {
        super.onStop();
        restoreStereoAudio();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        mMenuStereoAudio = menu.add(0, getMenuActivityId(MENU_STEREO_AUDIO), 0,
                R.string.single_track);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        updateStereoAudioIcon();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        super.onOptionsItemSelected(item);
        if(getMenuOriginalId(item.getItemId()) == MENU_STEREO_AUDIO) {
            mCurrentStereoAudio = !mCurrentStereoAudio;
            setStereoAudio(mCurrentStereoAudio);
            return true;
        }
        return false;
    }

    private boolean getStereoAudio() {
        boolean isstereo = false;
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        }
        final String stereo = mAudioManager.getParameters(KEY_STEREO);
        final String key = KEY_STEREO + "=1";
        if (stereo != null && stereo.indexOf(key) > -1) {
            isstereo = true;
        } else {
            isstereo = false;
        }
        if (LOG) {
            Log.v(TAG, "getStereoAudio() isstereo=" + isstereo + ", stereo=" + stereo
                    + ", key=" + key);
        }
        return isstereo;
    }

    private void setStereoAudio(final boolean flag) {
        final String value = KEY_STEREO + "=" + (flag ? "1" : "0");
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        }
        mAudioManager.setParameters(value);
        if (LOG) {
            Log.v(TAG, "setStereoAudio(" + flag + ") value=" + value);
        }
    }

    private void updateStereoAudioIcon() {
        if (mMenuStereoAudio != null) {
            mMenuStereoAudio.setTitle(mCurrentStereoAudio?R.string.single_track:R.string.stereo);
            mMenuStereoAudio.setIcon(mCurrentStereoAudio?R.drawable.ic_menu_single_track:R.drawable.ic_menu_stereo);
        }
    }

    private void enableStereoAudio() {
        if (LOG) {
            Log.v(TAG, "enableStereoAudio() mIsInitedStereoAudio=" + mIsInitedStereoAudio
                    + ", mCurrentStereoAudio=" + mCurrentStereoAudio);
        }
        mSystemStereoAudio = getStereoAudio();
        if (!mIsInitedStereoAudio) {
            mCurrentStereoAudio = mSystemStereoAudio;
            mIsInitedStereoAudio = true;
        } else {
            // restore old stereo type
            setStereoAudio(mCurrentStereoAudio);
        }
        updateStereoAudioIcon();
    }

    private void restoreStereoAudio() {
        setStereoAudio(mSystemStereoAudio);
    }
}
