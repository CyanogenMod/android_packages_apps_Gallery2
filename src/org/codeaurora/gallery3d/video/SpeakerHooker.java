/*
Copyright (c) 2014, The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.codeaurora.gallery3d.video;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.os.Bundle;

import com.android.gallery3d.R;

public class SpeakerHooker extends MovieHooker {

    private static final int MENU_SPEAKER = 1;

    private AudioManager mAudioManager;

    private MenuItem mMenuSpeakerButton;

    private boolean mIsHeadsetOn = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initAudioManager();
    }

    private void initAudioManager() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        }
    }

    @Override
    public void onDestroy() {
        turnSpeakerOff();
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        registerHeadSetReceiver();
    }

    private void registerHeadSetReceiver() {
        final IntentFilter intentFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        getContext().registerReceiver(mHeadSetReceiver, intentFilter);
    }

    @Override
    public void onPause() {
        unregisterHeadSetReceiver();
        super.onPause();
    }

    private void unregisterHeadSetReceiver() {
        try {
            getContext().unregisterReceiver(mHeadSetReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final BroadcastReceiver mHeadSetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (mAudioManager == null) {
                initAudioManager();
            }
            if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                mIsHeadsetOn = (intent.getIntExtra("state", 0) == 1)
                        || mAudioManager.isBluetoothA2dpOn();
            } else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)
                    || action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                final int deviceClass = ((BluetoothDevice)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE))
                                .getBluetoothClass().getDeviceClass();
                if ((deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES)
                        || (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET)) {
                    mIsHeadsetOn = action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)
                            || mAudioManager.isWiredHeadsetOn();
                }
            } else if (action.equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                mIsHeadsetOn = false;
            }
            updateSpeakerButton();
            if (!mIsHeadsetOn) {
                turnSpeakerOff();
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        mMenuSpeakerButton = menu.add(0, getMenuActivityId(MENU_SPEAKER), 0, R.string.speaker_on);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (getMenuOriginalId(item.getItemId())) {
            case MENU_SPEAKER:
                changeSpeakerState();
                return true;
            default:
                return false;
        }
    }

    private void changeSpeakerState() {
        if (isSpeakerOn()) {
            turnSpeakerOff();
        } else {
            if (mIsHeadsetOn) {
                turnSpeakerOn();
            } else {
                Toast.makeText(getContext(), getContext().getString(R.string.speaker_need_headset),
                        Toast.LENGTH_SHORT).show();
            }
        }
        updateSpeakerButton();
    }

    private void turnSpeakerOn() {
        if (mAudioManager == null) {
            initAudioManager();
        }
        mAudioManager.setSpeakerphoneOn(true);
        AudioSystem.setForceUse(AudioSystem.FOR_MEDIA,
                AudioSystem.FORCE_SPEAKER);
    }

    private void turnSpeakerOff() {
        if (mAudioManager == null) {
            initAudioManager();
        }
        AudioSystem.setForceUse(AudioSystem.FOR_MEDIA,
                AudioSystem.FORCE_NONE);
        mAudioManager.setSpeakerphoneOn(false);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        updateSpeakerButton();
        return true;
    }

    private void updateSpeakerButton() {
        if (mMenuSpeakerButton != null) {
            if (isSpeakerOn()) {
                mMenuSpeakerButton.setTitle(R.string.speaker_off);
            } else {
                mMenuSpeakerButton.setTitle(R.string.speaker_on);
            }
        }
    }

    private boolean isSpeakerOn() {
        boolean isSpeakerOn = false;
        if (mAudioManager == null) {
            initAudioManager();
        }
        isSpeakerOn = mAudioManager.isSpeakerphoneOn();
        return isSpeakerOn;
    }

}
