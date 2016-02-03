package org.codeaurora.gallery3d.video;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.RingtonePreference;
import android.preference.PreferenceScreen;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.Settings.System;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import com.android.gallery3d.R;

import java.util.ArrayList;

public class SettingsActivity extends PreferenceActivity {

    private static final String LOG_TAG = "SettingsActivity";

    public  static final String PREFERENCE_RTP_MINPORT = "rtp_min_port";
    public  static final String PREFERENCE_RTP_MAXPORT = "rtp_max_port";
    private static final String PREFERENCE_KEEP_ALIVE_INTERVAL_SECOND = "keep_alive_interval_second";
    private static final String PREFERENCE_CACHE_MIN_SIZE = "cache_min_size";
    private static final String PREFERENCE_CACHE_MAX_SIZE = "cache_max_size";
    public  static final String PREFERENCE_BUFFER_SIZE = "buffer_size";
    public  static final String PREFERENCE_APN = "apn";
    private static final String PACKAGE_NAME  = "com.android.settings";

    private static final int DEFAULT_RTP_MINPORT = 8192;
    private static final int DEFAULT_RTP_MAXPORT = 65535;
    private static final int DEFAULT_CACHE_MIN_SIZE = 4 * 1024 * 1024;
    private static final int DEFAULT_CACHE_MAX_SIZE = 20 * 1024 * 1024;
    private static final int DEFAULT_KEEP_ALIVE_INTERVAL_SECOND = 15;
    
    private static final int RTP_MIN_PORT = 1;
    private static final int RTP_MAX_PORT = 2;
    private static final int BUFFER_SIZE  = 3;
    
    private SharedPreferences  mPref;
    private EditTextPreference mRtpMinPort;
    private EditTextPreference mRtpMaxPort;
    private EditTextPreference mBufferSize;
    private PreferenceScreen   mApn;
    
    private static final int    SELECT_APN = 1;
    public  static final String PREFERRED_APN_URI = "content://telephony/carriers/preferapn";
    private static final Uri    PREFERAPN_URI = Uri.parse(PREFERRED_APN_URI);
    private static final int    COLUMN_ID_INDEX = 0;
    private static final int    NAME_INDEX = 1;
    private static final String RTP_PORTS_PROPERTY_NAME = "persist.env.media.rtp-ports";
    private static final String CACHE_PROPERTY_NAME = "persist.env.media.cache-params";

    private boolean mUseNvOperatorForEhrpd = SystemProperties.getBoolean(
            "persist.radio.use_nv_for_ehrpd", false);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.rtsp_settings_preferences);

        mPref = getPreferenceScreen().getSharedPreferences();
        mRtpMinPort = (EditTextPreference) findPreference(PREFERENCE_RTP_MINPORT);
        mRtpMaxPort = (EditTextPreference) findPreference(PREFERENCE_RTP_MAXPORT);
        mBufferSize = (EditTextPreference) findPreference(PREFERENCE_BUFFER_SIZE);
        mApn = (PreferenceScreen) findPreference(PREFERENCE_APN);
        
        setPreferenceListener(RTP_MIN_PORT, mRtpMinPort);
        setPreferenceListener(RTP_MAX_PORT, mRtpMaxPort);
        setPreferenceListener(BUFFER_SIZE, mBufferSize);
        setApnListener();

        ActionBar ab = getActionBar();
        ab.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        ab.setDisplayHomeAsUpEnabled(true);
        ab.setTitle(R.string.setting);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SELECT_APN) {
            setResult(resultCode);
            finish();
            Log.w(LOG_TAG, "onActivityResult requestCode = " + requestCode);
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO Auto-generated method stub
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }

    private String getOperatorNumericSelection() {
        String[] mccmncs = getOperatorNumeric();
        String where;
        where = (mccmncs[0] != null) ? "numeric=\"" + mccmncs[0] + "\"" : "";
        where += (mccmncs[1] != null) ? " or numeric=\"" + mccmncs[1] + "\"" : "";
        Log.d(LOG_TAG, "getOperatorNumericSelection: " + where);
        return where;
    }

    private String[] getOperatorNumeric() {
        ArrayList<String> result = new ArrayList<String>();
        String mccMncFromSim = null;
        if (mUseNvOperatorForEhrpd) {
            String mccMncForEhrpd = SystemProperties.get("ro.cdma.home.operator.numeric", null);
            if (mccMncForEhrpd != null && mccMncForEhrpd.length() > 0) {
                result.add(mccMncForEhrpd);
            }
        }

        mccMncFromSim = TelephonyManager.getDefault().getSimOperator();

        if (mccMncFromSim != null && mccMncFromSim.length() > 0) {
            result.add(mccMncFromSim);
        }
        return result.toArray(new String[2]);
    }

    private void enableRtpPortSetting() {
        final String rtpMinPortStr = mPref.getString(PREFERENCE_RTP_MINPORT, 
                Integer.toString(DEFAULT_RTP_MINPORT));
        final String rtpMaxPortStr = mPref.getString(PREFERENCE_RTP_MAXPORT,
                Integer.toString(DEFAULT_RTP_MAXPORT));
        // System property format: "rtpMinPort/rtpMaxPort"
        final String propertyValue = rtpMinPortStr + "/" + rtpMaxPortStr;
        Log.v(LOG_TAG, "set system property " + RTP_PORTS_PROPERTY_NAME + " : " + propertyValue);
        SystemProperties.set(RTP_PORTS_PROPERTY_NAME, propertyValue);
    }

    private void enableBufferSetting() {
        final String bufferSizeStr = mPref.getString(PREFERENCE_BUFFER_SIZE, 
                Integer.toString(DEFAULT_CACHE_MAX_SIZE));
        final int cacheMaxSize;
        final String ACTION_NAME = "org.codeaurora.gallery3d.video.STREAMING_SETTINGS_ENABLER";
        try {
            cacheMaxSize = Integer.valueOf(bufferSizeStr);
        } catch (NumberFormatException e) {
            Log.e(LOG_TAG, "Failed to parse cache max size");
            return;
        }
        // System property format: "minCacheSizeKB/maxCacheSizeKB/keepAliveIntervalSeconds"
        final String propertyValue = (DEFAULT_CACHE_MIN_SIZE / 1024) + "/" +
                (cacheMaxSize / 1024) + "/" + DEFAULT_KEEP_ALIVE_INTERVAL_SECOND;
        Log.v(LOG_TAG, "set system property " + CACHE_PROPERTY_NAME + " : " + propertyValue);
        SystemProperties.set(CACHE_PROPERTY_NAME, propertyValue);
    }
    
    private void setPreferenceListener(final int which, final EditTextPreference etp) {

        final String DIGITS_ACCEPTABLE = "0123456789";
        String summaryStr = "";
        String preferStr  = "";

        switch (which) {
            case RTP_MIN_PORT:
                preferStr = mPref.getString(PREFERENCE_RTP_MINPORT,
                        Integer.toString(DEFAULT_RTP_MINPORT));
                summaryStr = "streaming_min_udp_port";
                break;
            case RTP_MAX_PORT:
                preferStr = mPref.getString(PREFERENCE_RTP_MAXPORT, 
                        Integer.toString(DEFAULT_RTP_MAXPORT));
                summaryStr = "streaming_max_udp_port";
                break;
            case BUFFER_SIZE:
                preferStr = mPref.getString(PREFERENCE_BUFFER_SIZE, 
                        Integer.toString(DEFAULT_CACHE_MAX_SIZE));
                break;
            default:
                return;
            
        }

        final String summaryString = summaryStr; 
        etp.getEditText().setKeyListener(DigitsKeyListener.getInstance(DIGITS_ACCEPTABLE));
        etp.setSummary(preferStr);
        etp.setText(preferStr);
        etp.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final String summary = newValue.toString();
                final int value;
                try {
                    value = Integer.valueOf(summary);
                } catch (NumberFormatException e) {
                    Log.e(LOG_TAG, "NumberFormatException");
                    return false;
                }
                etp.setSummary(summary);
                etp.setText(summary);
                Log.d(LOG_TAG, "z66/z82 summary = " + summary);
                if(which == RTP_MIN_PORT || which == RTP_MAX_PORT) {
                    System.putString(getContentResolver(), summaryString, summary);
                    enableRtpPortSetting();
                } else {
                    enableBufferSetting();
                }
                return true;
            }
        });

    }
    
    private void setApnListener() {
        final String SUBSCRIPTION_KEY = "subscription";
        final String SUB_ID = "sub_id";
        mApn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(Settings.ACTION_APN_SETTINGS);
                int subscription = 0;
                try {
                    subscription = Settings.Global.getInt(SettingsActivity.this.getContentResolver(),
                            Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION);
                } catch (Exception e) {
                    Log.d("SettingActivity", "Can't get subscription for Exception: " + e);
                } finally {
                    intent.putExtra(SUBSCRIPTION_KEY, subscription);
                    intent.putExtra(SUB_ID, subscription);
                }
                startActivityForResult(intent, SELECT_APN);
                return true;
            }
        });
    }
}
