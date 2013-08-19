package org.codeaurora.gallery3d.video;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings.System;
import android.util.Log;

public class DmReceiver extends BroadcastReceiver {
    private static final String TAG = "DmReceiver";
    public static final String WRITE_SETTING_ACTION = "streaming.action.WRITE_SETTINGS";
    public static final String BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";

    private SharedPreferences mPref;
    static final int STREAMING_CONNPROFILE_IO_HANDLER_TYPE = 1;
    static final int STREAMING_MAX_UDP_PORT_IO_HANDLER_TYPE = 3;
    static final int STREAMING_MIN_UDP_PORT_IO_HANDLER_TYPE = 4;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mPref == null) {
            mPref = PreferenceManager.getDefaultSharedPreferences(context);
        }
        if (BOOT_COMPLETED.equals(intent.getAction())) {
            String rtpMaxport = mPref.getString(SettingsActivity.PREFERENCE_RTP_MAXPORT, "65535");
            String rtpMinport = mPref.getString(SettingsActivity.PREFERENCE_RTP_MINPORT, "8192");
            String apn = mPref.getString(SettingsActivity.PREFERENCE_APN, "CMWAP");
            System.putString(context.getContentResolver(),
                    "streaming_max_udp_port", rtpMaxport);
            System.putString(context.getContentResolver(),
                    "streaming_min_udp_port", rtpMinport);
            System.putString(context.getContentResolver(), "apn", apn);
        } else if (WRITE_SETTING_ACTION.equals(intent.getAction())) {
            int valueType = intent.getIntExtra("type", 0);
            String value = intent.getStringExtra("value");
            if (valueType == STREAMING_MAX_UDP_PORT_IO_HANDLER_TYPE) {
                mPref.edit().putString(SettingsActivity.PREFERENCE_RTP_MAXPORT,
                        value).commit();
                System.putString(context.getContentResolver(),
                        "streaming_max_udp_port", value);
            } else if (valueType == STREAMING_MIN_UDP_PORT_IO_HANDLER_TYPE) {
                mPref.edit().putString(SettingsActivity.PREFERENCE_RTP_MINPORT,
                        value).commit();
                System.putString(context.getContentResolver(),
                        "streaming_min_udp_port", value);
            } else if (valueType == STREAMING_CONNPROFILE_IO_HANDLER_TYPE) {
                mPref.edit().putString(SettingsActivity.PREFERENCE_APN,
                        value).commit();
                System.putString(context.getContentResolver(),
                        "apn", value);
            }
        }
    }
}
