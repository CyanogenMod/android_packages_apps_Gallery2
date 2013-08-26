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
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.Switch;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.ui.Knob;

/**
 * This activity plays a video from a specified URI.
 *
 * The client of this activity can pass a logo bitmap in the intent (KEY_LOGO_BITMAP)
 * to set the action bar logo so the playback process looks more seamlessly integrated with
 * the original activity.
 */
public class MovieActivity extends Activity {
    @SuppressWarnings("unused")
    private static final String TAG = "MovieActivity";
    public static final String KEY_LOGO_BITMAP = "logo-bitmap";
    public static final String KEY_TREAT_UP_AS_BACK = "treat-up-as-back";

    private MoviePlayer mPlayer;
    private boolean mFinishOnCompletion;
    private Uri mUri;

    private static final short BASSBOOST_MAX_STRENGTH = 1000;
    private static final short VIRTUALIZER_MAX_STRENGTH = 1000;

    private boolean mIsHeadsetOn = false;
    private boolean mVirtualizerSupported = false;
    private boolean mBassBoostSupported = false;

    private SharedPreferences mPrefs;
    static enum Key {
        global_enabled, bb_strength, virt_strength
    };

    private BassBoost mBassBoostEffect;
    private Virtualizer mVirtualizerEffect;
    private AlertDialog mEffectDialog;
    private Switch mSwitch;
    private Knob mBassBoostKnob;
    private Knob mVirtualizerKnob;

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
                mIsHeadsetOn = audioManager.isBluetoothA2dpOn() || audioManager.isWiredHeadsetOn();
            }
            if (mEffectDialog != null) {
                if (!mIsHeadsetOn && mEffectDialog.isShowing()) {
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
        initializeActionBar(intent);
        mFinishOnCompletion = intent.getBooleanExtra(
                MediaStore.EXTRA_FINISH_ON_COMPLETION, true);
        mPlayer = new MoviePlayer(rootView, this, intent.getData(), savedInstanceState,
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

        // Determine available/supported effects
        final Descriptor[] effects = AudioEffect.queryEffects();
        for (final Descriptor effect : effects) {
            if (effect.type.equals(AudioEffect.EFFECT_TYPE_VIRTUALIZER)) {
                mVirtualizerSupported = true;
            } else if (effect.type.equals(AudioEffect.EFFECT_TYPE_BASS_BOOST)) {
                mBassBoostSupported = true;
            }
        }

        mPrefs = getSharedPreferences(getApplicationContext().getPackageName(),
                Context.MODE_PRIVATE);

        mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
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
                ActionBar.DISPLAY_HOME_AS_UP,
                ActionBar.DISPLAY_HOME_AS_UP);

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

        // Document says EXTRA_STREAM should be a content: Uri
        // So, we only share the video if it's "content:".
        MenuItem shareItem = menu.findItem(R.id.action_share);
        if (isSharable()) {
            shareItem.setVisible(true);
            ((ShareActionProvider) shareItem.getActionProvider())
                    .setShareIntent(createShareIntent());
        } else {
            shareItem.setVisible(false);
        }

        final MenuItem mi = menu.add(R.string.audio_effects);
        mi.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                onAudioEffectsMenuItemClick();
                return true;
            }
        });
        return true;
    }

    private void onAudioEffectsMenuItemClick() {
        if (!mIsHeadsetOn) {
            showHeadsetPlugToast();
        } else {
            LayoutInflater factory = LayoutInflater.from(this);
            final View content = factory.inflate(R.layout.audio_effects_dialog, null);

            boolean enabled = mPrefs.getBoolean(Key.global_enabled.toString(), false);

            mSwitch = (Switch) content.findViewById(R.id.audio_effects_switch);
            mSwitch.setChecked(enabled);
            mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mBassBoostEffect.setEnabled(isChecked);
                    mVirtualizerEffect.setEnabled(isChecked);
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
                public void onStartTrackingTouch(Knob knob) {
                }

                @Override
                public void onStopTrackingTouch(Knob knob) {
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
                public void onStartTrackingTouch(Knob knob) {
                }

                @Override
                public void onStopTrackingTouch(Knob knob) {
                }

                @Override
                public boolean onSwitchChanged(Knob knob, boolean enabled) {
                    return false;
                }
            });

            mEffectDialog = new AlertDialog.Builder(MovieActivity.this,
                    AlertDialog.THEME_HOLO_DARK)
                .setTitle(R.string.audio_effects)
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
                        mBassBoostEffect.setStrength((short)
                                mPrefs.getInt(Key.bb_strength.toString(), 0));
                        mBassBoostEffect.setEnabled(enabled);
                        mVirtualizerEffect.setStrength((short)
                            mPrefs.getInt(Key.virt_strength.toString(), 0));
                        mVirtualizerEffect.setEnabled(enabled);
                    }
                })
                .create();
            mEffectDialog.show();
        }
    }

    private void initEffects(int sessionId) {
        // Singleton instance
        if ((mBassBoostEffect == null) && mBassBoostSupported) {
            mBassBoostEffect = new BassBoost(0, sessionId);
        }

        if ((mVirtualizerEffect == null) && mVirtualizerSupported) {
            mVirtualizerEffect = new Virtualizer(0, sessionId);
        }

        if (mIsHeadsetOn) {
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
            }
        }

    }

    private void releaseEffects() {
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
        intent.putExtra(Intent.EXTRA_STREAM, mUri);
        return intent;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // If click back up button, we will always finish current activity and back to previous one.
            finish();
            return true;
        } else if (id == R.id.action_share) {
            startActivity(Intent.createChooser(createShareIntent(),
                    getString(R.string.share)));
            return true;
        }
        return false;
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
    }

    @Override
    protected void onStop() {
        ((AudioManager) getSystemService(AUDIO_SERVICE))
                .abandonAudioFocus(null);
        super.onStop();
    }

    @Override
    public void onPause() {
        // Audio track will be deallocated for local video playback,
        // thus recycle effect here.
        releaseEffects();
        mPlayer.onPause();
        try {
            unregisterReceiver(mReceiver);
        } catch (IllegalArgumentException e) {
            // Do nothing
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        invalidateOptionsMenu();
        mPlayer.onResume();
        if ((mVirtualizerSupported) || (mBassBoostSupported)) {
            final IntentFilter intentFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
            intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            registerReceiver(mReceiver, intentFilter);
        }

        initEffects(mPlayer.getAudioSessionId());
        super.onResume();
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
}
