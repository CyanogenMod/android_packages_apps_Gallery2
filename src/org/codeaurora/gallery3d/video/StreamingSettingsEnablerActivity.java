/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.codeaurora.gallery3d.video;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;

public class StreamingSettingsEnablerActivity extends Activity {

    private static final String LOG_TAG = "StreamingSettingsEnablerActivity";

    private static final String PREFERENCE_RTP_MINPORT = "rtp_min_port";
    private static final String PREFERENCE_RTP_MAXPORT = "rtp_max_port";
    private static final String PREFERENCE_KEEP_ALIVE_INTERVAL_SECOND =
            "keep_alive_interval_second";
    private static final String PREFERENCE_CACHE_MIN_SIZE = "cache_min_size";
    private static final String PREFERENCE_CACHE_MAX_SIZE = "cache_max_size";

    private static final int DEFAULT_RTP_MINPORT = 15550;
    private static final int DEFAULT_RTP_MAXPORT = 65535;
    private static final int DEFAULT_CACHE_MIN_SIZE = 4 * 1024 * 1024;
    private static final int DEFAULT_CACHE_MAX_SIZE = 20 * 1024 * 1024;
    private static final int DEFAULT_KEEP_ALIVE_INTERVAL_SECOND = 15;
    private static final String RTP_PORTS_PROPERTY_NAME = "persist.env.media.rtp-ports";
    private static final String CACHE_PROPERTY_NAME = "persist.env.media.cache-params";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        final int rtpMinPort = intent.getIntExtra(PREFERENCE_RTP_MINPORT, DEFAULT_RTP_MINPORT);
        final int rtpMaxPort = intent.getIntExtra(PREFERENCE_RTP_MAXPORT, DEFAULT_RTP_MAXPORT);
        enableRtpPortSetting(rtpMinPort, rtpMaxPort);

        final int cacheMinSize =
                intent.getIntExtra(PREFERENCE_CACHE_MIN_SIZE, DEFAULT_CACHE_MIN_SIZE);
        final int cacheMaxSize =
                intent.getIntExtra(PREFERENCE_CACHE_MAX_SIZE, DEFAULT_CACHE_MAX_SIZE);
        final int keepAliveIntervalSec =
                intent.getIntExtra(PREFERENCE_KEEP_ALIVE_INTERVAL_SECOND,
                DEFAULT_KEEP_ALIVE_INTERVAL_SECOND);
        enableCacheSetting(cacheMinSize, cacheMaxSize, keepAliveIntervalSec);
        finish();
    }


    private void enableRtpPortSetting(final int rtpMinPort, final int rtpMaxPort) {
        // System property format: "rtpMinPort/rtpMaxPort"
        final String propertyValue = rtpMinPort + "/" + rtpMaxPort;
        Log.v(LOG_TAG, "set system property " + RTP_PORTS_PROPERTY_NAME + " : " + propertyValue);
        SystemProperties.set(RTP_PORTS_PROPERTY_NAME, propertyValue);
    }

    private void enableCacheSetting(final int cacheMinSize,
            final int cacheMaxSize, final int keepAliveIntervalSec) {
        // System property format: "minCacheSizeKB/maxCacheSizeKB/keepAliveIntervalSeconds"
        final String propertyValue = (cacheMinSize / 1024) + "/" +
                (cacheMaxSize / 1024) + "/" + keepAliveIntervalSec;
        Log.v(LOG_TAG, "set system property " + CACHE_PROPERTY_NAME + " : " + propertyValue);
        SystemProperties.set(CACHE_PROPERTY_NAME, propertyValue);
    }
}
