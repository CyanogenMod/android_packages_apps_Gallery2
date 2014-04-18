/*
Copyright (c) 2013-2014, The Linux Foundation. All rights reserved.

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

import android.content.Context;

import com.android.gallery3d.app.MovieActivity;
import com.android.gallery3d.R;

import org.codeaurora.gallery3d.ext.ActivityHookerGroup;
import org.codeaurora.gallery3d.ext.IActivityHooker;

import java.util.ArrayList;
import java.util.List;

public class ExtensionHelper {

    public static IActivityHooker getHooker(final Context context) {

        final ActivityHookerGroup group = new ActivityHookerGroup();
        boolean loop = context.getResources().getBoolean(R.bool.loop);
        boolean stereo = context.getResources().getBoolean(R.bool.stereo);
        boolean streaming = context.getResources().getBoolean(R.bool.streaming);
        boolean playlist = context.getResources().getBoolean(R.bool.playlist);
        boolean speaker = context.getResources().getBoolean(R.bool.speaker);

        if (loop == true) {
            group.addHooker(new LoopVideoHooker()); // add it for common feature.
        }
        if (stereo == true) {
            group.addHooker(new StereoAudioHooker()); // add it for common feature.
        }
        if (streaming == true) {
            group.addHooker(new StreamingHooker());
            group.addHooker(new BookmarkHooker());
        }
        if (playlist == true) {
            group.addHooker(new MovieListHooker()); // add it for common feature.
            group.addHooker(new StepOptionSettingsHooker());
        }
        if (speaker == true) {
            group.addHooker(new SpeakerHooker());
        }
        return group;
    }
}
