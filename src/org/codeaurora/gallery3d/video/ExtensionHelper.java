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

        if (loop == true) {
            group.addHooker(new LoopVideoHooker()); // add it for common feature.
        }
        if (stereo == true) {
            group.addHooker(new StereoAudioHooker()); // add it for common feature.
        }

        return group;
    }
}
