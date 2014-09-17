/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.app;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.AudioEffect.Descriptor;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Virtualizer;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ShareActionProvider;
import android.widget.ToggleButton;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.ui.Knob;
import org.codeaurora.gallery3d.ext.IActivityHooker;
import org.codeaurora.gallery3d.ext.IMovieItem;
import org.codeaurora.gallery3d.ext.MovieItem;
import org.codeaurora.gallery3d.ext.MovieUtils;
import org.codeaurora.gallery3d.video.ExtensionHelper;
import org.codeaurora.gallery3d.video.MovieTitleHelper;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;

/**
 * This activity plays a video from a specified URI.
 *
 * The client of this activity can pass a logo bitmap in the intent (KEY_LOGO_BITMAP)
 * to set the action bar logo so the playback process looks more seamlessly integrated with
 * the original activity.
 */
public class MovieActivity extends Activity {
    @SuppressWarnings("unused")
    private static final String  TAG = "MovieActivity";
    private static final boolean LOG = false;
    public  static final String  KEY_LOGO_BITMAP = "logo-bitmap";
    public  static final String  KEY_TREAT_UP_AS_BACK = "treat-up-as-back";
    private static final String  VIDEO_SDP_MIME_TYPE = "application/sdp";
    private static final String  VIDEO_SDP_TITLE = "rtsp://";
    private static final String  VIDEO_FILE_SCHEMA = "file";
    private static final String  VIDEO_MIME_TYPE = "video/*";
    private static final String  SHARE_HISTORY_FILE = "video_share_history_file";

    private MoviePlayer mPlayer;
    private boolean     mFinishOnCompletion;
    private Uri         mUri;

    private static final short BASSBOOST_MAX_STRENGTH   = 1000;
    private static final short VIRTUALIZER_MAX_STRENGTH = 1000;

    private boolean mIsHeadsetOn = false;
    private boolean mVirtualizerSupported = false;
    private boolean mBassBoostSupported = false;

    static enum Key {
        global_enabled, bb_strength, virt_strength
    };

    private BassBoost   mBassBoostEffect;
    private Virtualizer mVirtualizerEffect;
    private AlertDialog mEffectDialog;
    private ToggleButton mSwitch;
    private Knob        mBassBoostKnob;
    private Knob        mVirtualizerKnob;

    private SharedPreferences   mPrefs;
    private ShareActionProvider mShareProvider;
    private IMovieItem          mMovieItem;
    private IActivityHooker     mMovieHooker;
    private KeyguardManager     mKeyguardManager;

    private boolean mResumed        = false;
    private boolean mControlResumed = false;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            final AudioManager audioManager =
                (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                mIsHeadsetOn = (intent.getIntExtra("state", 0) == 1)
                        || audioManager.isBluetoothA2dpOn();
            } else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)
                    || action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                final int deviceClass = ((BluetoothDevice)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE))
                        .getBluetoothClass().getDeviceClass();
                if ((deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES)
                        || (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET)) {
                    mIsHeadsetOn = action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)
                            || audioManager.isWiredHeadsetOn();
                }
            } else if (action.equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                mIsHeadsetOn = false;
            }
            if (mEffectDialog != null) {
                if (!mIsHeadsetOn && !isBtHeadsetConnected() && mEffectDialog.isShowing()) {
                    mEffectDialog.dismiss();
                    showHeadsetPlugToast();
                }
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setSystemUiVisibility(View rootView) {
        if (ApiHelper.HAS_VIEW_SYSTEM_UI_FLAG_LAYOUT_STABLE) {
            rootView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

        setContentView(R.layout.movie_view);
        View rootView = findViewById(R.id.movie_view_root);

        setSystemUiVisibility(rootView);

        Intent intent = getIntent();

        mMovieHooker = ExtensionHelper.getHooker(this);
        initMovieInfo(intent);

        initializeActionBar(intent);
        mFinishOnCompletion = intent.getBooleanExtra(
                MediaStore.EXTRA_FINISH_ON_COMPLETION, true);
        mPrefs = getSharedPreferences(getApplicationContext().getPackageName(),
                Context.MODE_PRIVATE);
        mPlayer = new MoviePlayer(rootView, this, mMovieItem, savedInstanceState,
                !mFinishOnCompletion) {
            @Override
            public void onCompletion() {
                if (mFinishOnCompletion) {
                    finish();
                }
            }
        };
        if (intent.hasExtra(MediaStore.EXTRA_SCREEN_ORIENTATION)) {
            int orientation = intent.getIntExtra(
                    MediaStore.EXTRA_SCREEN_ORIENTATION,
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (orientation != getRequestedOrientation()) {
                setRequestedOrientation(orientation);
            }
        }
        Window win = getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        winParams.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
        winParams.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        win.setAttributes(winParams);

        // We set the background in the theme to have the launching animation.
        // But for the performance (and battery), we remove the background here.
        win.setBackgroundDrawable(null);
        mMovieHooker.init(this, intent);
        mMovieHooker.setParameter(null, mPlayer.getMoviePlayerExt());
        mMovieHooker.setParameter(null, mMovieItem);
        mMovieHooker.setParameter(null, mPlayer.getVideoSurface());
        mMovieHooker.onCreate(savedInstanceState);

        // Determine available/supported effects
        final Descriptor[] effects = AudioEffect.queryEffects();
        for (final Descriptor effect : effects) {
            if (effect.type.equals(AudioEffect.EFFECT_TYPE_VIRTUALIZER)) {
                mVirtualizerSupported = true;
            } else if (effect.type.equals(AudioEffect.EFFECT_TYPE_BASS_BOOST)) {
                mBassBoostSupported = true;
            }
        }

        mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mPlayer.onPrepared(mp);
                initEffects(mp.getAudioSessionId());
            }
        });
    }

    private void setActionBarLogoFromIntent(Intent intent) {
        Bitmap logo = intent.getParcelableExtra(KEY_LOGO_BITMAP);
        if (logo != null) {
            getActionBar().setLogo(
                    new BitmapDrawable(getResources(), logo));
        }
    }

    private void initializeActionBar(Intent intent) {
        mUri = intent.getData();
        final ActionBar actionBar = getActionBar();
        if (actionBar == null) {
            return;
        }
        setActionBarLogoFromIntent(intent);
        actionBar.setDisplayOptions(
                ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE,
                ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);

        String title = intent.getStringExtra(Intent.EXTRA_TITLE);
        if (title != null) {
            actionBar.setTitle(title);
        } else {
            // Displays the filename as title, reading the filename from the
            // interface: {@link android.provider.OpenableColumns#DISPLAY_NAME}.
            AsyncQueryHandler queryHandler =
                    new AsyncQueryHandler(getContentResolver()) {
                @Override
                protected void onQueryComplete(int token, Object cookie,
                        Cursor cursor) {
                    try {
                        if ((cursor != null) && cursor.moveToFirst()) {
                            String displayName = cursor.getString(0);

                            // Just show empty title if other apps don't set
                            // DISPLAY_NAME
                            actionBar.setTitle((displayName == null) ? "" :
                                    displayName);
                        }
                    } finally {
                        Utils.closeSilently(cursor);
                    }
                }
            };
            queryHandler.startQuery(0, null, mUri,
                    new String[] {OpenableColumns.DISPLAY_NAME}, null, null,
                    null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.movie, menu);
        MenuItem shareMenu = menu.findItem(R.id.action_share);
        ShareActionProvider provider = (ShareActionProvider) shareMenu.getActionProvider();
        mShareProvider = provider;
        if (mShareProvider != null) {
            // share provider is singleton, we should refresh our history file.
            mShareProvider.setShareHistoryFileName(SHARE_HISTORY_FILE);
        }
        refreshShareProvider(mMovieItem);

        final MenuItem mi = menu.add(R.string.audio_effects);
        mi.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                onAudioEffectsMenuItemClick();
                return true;
            }
        });
        mMovieHooker.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        mMovieHooker.onPrepareOptionsMenu(menu);
        return true;
    }

    private void onAudioEffectsMenuItemClick() {
        if (!mIsHeadsetOn && !isBtHeadsetConnected()) {
            showHeadsetPlugToast();
        } else {
            LayoutInflater factory = LayoutInflater.from(this);
            final View content = factory.inflate(R.layout.audio_effects_dialog, null);
            final View title = factory.inflate(R.layout.audio_effects_title, null);

            boolean enabled = mPrefs.getBoolean(Key.global_enabled.toString(), false);

            mSwitch = (ToggleButton) title.findViewById(R.id.audio_effects_switch);
            mSwitch.setChecked(enabled);
            mSwitch.setBackgroundResource(enabled ?
                    R.drawable.switch_thumb_activated : R.drawable.switch_thumb_off);

            mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mSwitch.setBackgroundResource(isChecked ?
                            R.drawable.switch_thumb_activated : R.drawable.switch_thumb_off);
                    if(mBassBoostEffect != null) {
                        mBassBoostEffect.setEnabled(isChecked);
                    }
                    if(mVirtualizerEffect != null) {
                        mVirtualizerEffect.setEnabled(isChecked);
                    }
                    mBassBoostKnob.setEnabled(isChecked);
                    mVirtualizerKnob.setEnabled(isChecked);
                }
            });

            mBassBoostKnob = (Knob) content.findViewById(R.id.bBStrengthKnob);
            mBassBoostKnob.setEnabled(enabled);
            mBassBoostKnob.setMax(BASSBOOST_MAX_STRENGTH);
            mBassBoostKnob.setValue(mPrefs.getInt(Key.bb_strength.toString(), 0));
            mBassBoostKnob.setOnKnobChangeListener(new Knob.OnKnobChangeListener() {
                @Override
                public void onValueChanged(Knob knob, int value, boolean fromUser) {
                    if(mBassBoostEffect != null) {
                        mBassBoostEffect.setStrength((short) value);
                    }
                }

                @Override
                public boolean onSwitchChanged(Knob knob, boolean enabled) {
                    return false;
                }
            });

            mVirtualizerKnob = (Knob) content.findViewById(R.id.vIStrengthKnob);
            mVirtualizerKnob.setEnabled(enabled);
            mVirtualizerKnob.setMax(VIRTUALIZER_MAX_STRENGTH);
            mVirtualizerKnob.setValue(mPrefs.getInt(Key.virt_strength.toString(), 0));
            mVirtualizerKnob.setOnKnobChangeListener(new Knob.OnKnobChangeListener() {
                @Override
                public void onValueChanged(Knob knob, int value, boolean fromUser) {
                    if(mVirtualizerEffect != null) {
                        mVirtualizerEffect.setStrength((short) value);
                    }
                }

                @Override
                public boolean onSwitchChanged(Knob knob, boolean enabled) {
                    return false;
                }
            });

            mEffectDialog = new AlertDialog.Builder(MovieActivity.this,
                    AlertDialog.THEME_HOLO_DARK)
                .setCustomTitle(title)
                .setView(content)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences.Editor editor = mPrefs.edit();
                        editor.putBoolean(Key.global_enabled.toString(), mSwitch.isChecked());
                        editor.putInt(Key.bb_strength.toString(), mBassBoostKnob.getValue());
                        editor.putInt(Key.virt_strength.toString(),
                                mVirtualizerKnob.getValue());
                        editor.commit();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean enabled = mPrefs.getBoolean(Key.global_enabled.toString(), false);
                        if(mBassBoostEffect != null) {
                            mBassBoostEffect.setStrength((short)
                                    mPrefs.getInt(Key.bb_strength.toString(), 0));
                            mBassBoostEffect.setEnabled(enabled);
                        }
                        if(mVirtualizerEffect != null) {
                            mVirtualizerEffect.setStrength((short)
                                mPrefs.getInt(Key.virt_strength.toString(), 0));
                            mVirtualizerEffect.setEnabled(enabled);
                        }
                    }
                })
                .setCancelable(false)
                .create();
            mEffectDialog.show();
            mEffectDialog.findViewById(com.android.internal.R.id.titleDivider)
                .setBackgroundResource(R.color.highlight);
        }
    }

    public void initEffects(int sessionId) {
        // Singleton instance
        if ((mBassBoostEffect == null) && mBassBoostSupported) {
            mBassBoostEffect = new BassBoost(0, sessionId);
        }

        if ((mVirtualizerEffect == null) && mVirtualizerSupported) {
            mVirtualizerEffect = new Virtualizer(0, sessionId);
        }

        if (mIsHeadsetOn || isBtHeadsetConnected()) {
            if (mPrefs.getBoolean(Key.global_enabled.toString(), false)) {
                if (mBassBoostSupported) {
                    mBassBoostEffect.setStrength((short)
                        mPrefs.getInt(Key.bb_strength.toString(), 0));
                    mBassBoostEffect.setEnabled(true);
                }
                if (mVirtualizerSupported) {
                    mVirtualizerEffect.setStrength((short)
                        mPrefs.getInt(Key.virt_strength.toString(), 0));
                    mVirtualizerEffect.setEnabled(true);
                }
            } else {
                if (mBassBoostSupported) {
                    mBassBoostEffect.setStrength((short)
                        mPrefs.getInt(Key.bb_strength.toString(), 0));
                }
                if (mVirtualizerSupported) {
                    mVirtualizerEffect.setStrength((short)
                        mPrefs.getInt(Key.virt_strength.toString(), 0));
                }
            }
        }

    }

    public void releaseEffects() {
        if (mBassBoostEffect != null) {
            mBassBoostEffect.setEnabled(false);
            mBassBoostEffect.release();
            mBassBoostEffect = null;
        }
        if (mVirtualizerEffect != null) {
            mVirtualizerEffect.setEnabled(false);
            mVirtualizerEffect.release();
            mVirtualizerEffect = null;
        }
    }

    private Intent createShareIntent() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("video/*");
        intent.putExtra(Intent.EXTRA_STREAM, mMovieItem.getUri());
        return intent;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
         // If click back up button, we will always finish current activity and 
         // back to previous one.
            finish();
            return true;
        } else if (id == R.id.action_share) {
            startActivity(Intent.createChooser(createShareIntent(),
                    getString(R.string.share)));
            return true;
        }
        return mMovieHooker.onOptionsItemSelected(item);
    }

    public void showHeadsetPlugToast() {
        final Toast toast = Toast.makeText(getApplicationContext(), R.string.headset_plug,
                Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, toast.getXOffset() / 2, toast.getYOffset() / 2);
        toast.show();
    }

    @Override
    public void onStart() {
        ((AudioManager) getSystemService(AUDIO_SERVICE))
                .requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        super.onStart();
        mMovieHooker.onStart();
        registerScreenOff();
    }

    @Override
    protected void onStop() {
        ((AudioManager) getSystemService(AUDIO_SERVICE))
                .abandonAudioFocus(null);
        super.onStop();
        if (mControlResumed && mPlayer != null) {
            mPlayer.onStop();
            mControlResumed = false;
        }
        mMovieHooker.onStop();
        unregisterScreenOff();
    }

    @Override
    public void onPause() {
        // Audio track will be deallocated for local video playback,
        // thus recycle effect here.
        releaseEffects();
        try {
            unregisterReceiver(mReceiver);
        } catch (IllegalArgumentException e) {
            // Do nothing
        }
        mResumed = false;
        if (mControlResumed && mPlayer != null) {
            mControlResumed = !mPlayer.onPause();
        }
        super.onPause();
        mMovieHooker.onPause();
    }

    @Override
    public void onResume() {
        invalidateOptionsMenu();

        if ((mVirtualizerSupported) || (mBassBoostSupported)) {
            final IntentFilter intentFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
            intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            registerReceiver(mReceiver, intentFilter);
        }

        mResumed = true;
        if (!isKeyguardLocked() && !mControlResumed && mPlayer != null) {
            mPlayer.onResume();
            mControlResumed = true;
            //initEffects(mPlayer.getAudioSessionId());
        }
        enhanceActionBar();
        super.onResume();
        mMovieHooker.onResume();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if(this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ||
            this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            mPlayer.setDefaultScreenMode();
        }
    }

    private boolean isBtHeadsetConnected() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if ((BluetoothProfile.STATE_CONNECTED == adapter.getProfileConnectionState(BluetoothProfile.HEADSET))
            || (BluetoothProfile.STATE_CONNECTED == adapter.getProfileConnectionState(BluetoothProfile.A2DP))) {
            return true;
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mPlayer.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        releaseEffects();
        mPlayer.onDestroy();
        super.onDestroy();
        mMovieHooker.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (LOG) {
            Log.v(TAG, "onWindowFocusChanged(" + hasFocus + ") isKeyguardLocked="
                    + isKeyguardLocked()
                    + ", mResumed=" + mResumed + ", mControlResumed=" + mControlResumed);
        }
        if (hasFocus && !isKeyguardLocked() && mResumed && !mControlResumed && mPlayer != null) {
            mPlayer.onResume();
            mControlResumed = true;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return mPlayer.onKeyDown(keyCode, event)
                || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return mPlayer.onKeyUp(keyCode, event)
                || super.onKeyUp(keyCode, event);
    }

    private boolean isSharable() {
        String scheme = mUri.getScheme();
        return ContentResolver.SCHEME_FILE.equals(scheme)
                || (ContentResolver.SCHEME_CONTENT.equals(scheme) && MediaStore.AUTHORITY
                        .equals(mUri.getAuthority()));
    }
    private void initMovieInfo(Intent intent) {
        Uri original = intent.getData();
        String mimeType = intent.getType();
        if (VIDEO_SDP_MIME_TYPE.equalsIgnoreCase(mimeType)
                && VIDEO_FILE_SCHEMA.equalsIgnoreCase(original.getScheme())) {
            mMovieItem = new MovieItem(VIDEO_SDP_TITLE + original, mimeType, null);
        } else {
            mMovieItem = new MovieItem(original, mimeType, null);
        }
        mMovieItem.setOriginalUri(original);
    }

    // we do not stop live streaming when other dialog overlays it.
    private BroadcastReceiver mScreenOffReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (LOG) {
                Log.v(TAG, "onReceive(" + intent.getAction() + ") mControlResumed="
                        + mControlResumed);
            }
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                // Only stop video.
                if (mControlResumed) {
                    mPlayer.onStop();
                    mControlResumed = false;
                }
            }
        }

    };

    private void registerScreenOff() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenOffReceiver, filter);
    }

    private void unregisterScreenOff() {
        unregisterReceiver(mScreenOffReceiver);
    }

    private boolean isKeyguardLocked() {
        if (mKeyguardManager == null) {
            mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        }
        // isKeyguardSecure excludes the slide lock case.
        boolean locked = (mKeyguardManager != null)
                && mKeyguardManager.inKeyguardRestrictedInputMode();
        if (LOG) {
            Log.v(TAG, "isKeyguardLocked() locked=" + locked + ", mKeyguardManager="
                    + mKeyguardManager);
        }
        return locked;
    }

    public void refreshMovieInfo(IMovieItem info) {
        mMovieItem = info;
        setActionBarTitle(info.getTitle());
        refreshShareProvider(info);
        mMovieHooker.setParameter(null, mMovieItem);
    }

    private void refreshShareProvider(IMovieItem info) {
        // we only share the video if it's "content:".
        if (mShareProvider != null) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            if (MovieUtils.isLocalFile(info.getUri(), info.getMimeType())) {
                intent.setType("video/*");
                intent.putExtra(Intent.EXTRA_STREAM, info.getUri());
            } else {
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, String.valueOf(info.getUri()));
            }
            mShareProvider.setShareIntent(intent);
        }
    }

    private void enhanceActionBar() {
        final IMovieItem movieItem = mMovieItem;// remember original item
        final Uri uri = mMovieItem.getUri();
        final String scheme = mMovieItem.getUri().getScheme();
        final String authority = mMovieItem.getUri().getAuthority();
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String title = null;
                if (ContentResolver.SCHEME_FILE.equals(scheme)) {
                    title = MovieTitleHelper.getTitleFromMediaData(MovieActivity.this, uri);
                } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
                    title = MovieTitleHelper.getTitleFromDisplayName(MovieActivity.this, uri);
                    if (title == null) {
                        title = MovieTitleHelper.getTitleFromData(MovieActivity.this, uri);
                    }
                }
                if (title == null) {
                    title = MovieTitleHelper.getTitleFromUri(uri);
                }
                if (LOG) {
                    Log.v(TAG, "enhanceActionBar() task return " + title);
                }
                return title;
            }

            @Override
            protected void onPostExecute(String result) {
                if (LOG) {
                    Log.v(TAG, "onPostExecute(" + result + ") movieItem=" + movieItem
                            + ", mMovieItem=" + mMovieItem);
                }
                movieItem.setTitle(result);
                if (movieItem == mMovieItem) {
                    setActionBarTitle(result);
                }
            };
        }.execute();
        if (LOG) {
            Log.v(TAG, "enhanceActionBar() " + mMovieItem);
        }
    }

    public void setActionBarTitle(String title) {
        if (LOG) {
            Log.v(TAG, "setActionBarTitle(" + title + ")");
        }
        ActionBar actionBar = getActionBar();
        if (title != null) {
            actionBar.setTitle(title);
        }
    }
}
