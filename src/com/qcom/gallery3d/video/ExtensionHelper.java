package com.qcom.gallery3d.video;

import android.content.Context;

import com.android.gallery3d.app.MovieActivity;
import com.qcom.gallery3d.ext.ActivityHookerGroup;
import com.qcom.gallery3d.ext.IActivityHooker;
import com.qcom.gallery3d.ext.IMovieExtension;
import com.qcom.gallery3d.ext.IMovieStrategy;
import com.qcom.gallery3d.ext.MovieExtension;
import com.qcom.gallery3d.ext.MovieStrategy;
import com.qcom.gallery3d.ext.QcomLog;

import java.util.ArrayList;
import java.util.List;

public class ExtensionHelper {
    private static final String TAG = "ExtensionHelper";
    private static final boolean LOG = true;
    
    private static List<IMovieExtension> sMovieExtensions;
    private static void ensureMovieExtension(final Context context) {
        if (sMovieExtensions == null) {
            sMovieExtensions = new ArrayList<IMovieExtension>();
            sMovieExtensions.add(new MovieExtension(MovieExtension.CMCC_EXTENSION_FUNCTIONS));
        }
    }

    public static IActivityHooker getHooker(final Context context) {
        ensureMovieExtension(context);
        final ActivityHookerGroup group = new ActivityHookerGroup();
        if (!(ExtensionHelper.getMovieStrategy(context).shouldEnableRewindAndForward())) {
            group.addHooker(new StopVideoHooker());//add it for common feature.
        }
        group.addHooker(new LoopVideoHooker()); //add it for common feature.

        for (final IMovieExtension ext : sMovieExtensions) { //add common feature in host app
            final List<Integer> list = ext.getFeatureList();
            if (list != null) {
                for (int i = 0, size = list.size(); i < size; i++) {
                    final int feature = list.get(i);
                    switch(feature) {
                    case IMovieExtension.FEATURE_ENABLE_STOP:
                        //group.addHooker(new StopVideoHooker());
                        break;
                   // case IMovieExtension.FEATURE_ENABLE_NOTIFICATION_PLUS:
                   //    group.addHooker(new NotificationPlusHooker());
                   //   break;
                    case IMovieExtension.FEATURE_ENABLE_STREAMING:
                        group.addHooker(new StreamingHooker());
                        break;
                    case IMovieExtension.FEATURE_ENABLE_BOOKMARK:
                        group.addHooker(new BookmarkHooker());
                        break;
                    case IMovieExtension.FEATURE_ENABLE_VIDEO_LIST:
                        group.addHooker(new MovieListHooker());
                        break;
                    case IMovieExtension.FEATURE_ENABLE_STEREO_AUDIO:
                        group.addHooker(new StereoAudioHooker());
                        break;
                    case IMovieExtension.FEATURE_ENABLE_SETTINGS:
                        group.addHooker(new StepOptionSettingsHooker());
                        break;
                    default:
                        break;
                    }
                }
            }
        }
        for (final IMovieExtension ext : sMovieExtensions) { //add other feature in plugin app
            final IActivityHooker hooker = ext.getHooker();
            if (hooker != null) {
                group.addHooker(hooker);
            }
        }
        for (int i = 0, count = group.size(); i < count; i++) {
            if (LOG) {
                QcomLog.v(TAG, "getHooker() [" + i + "]=" + group.getHooker(i));
            }
        }
        return group;
    }
    
    public static IMovieStrategy getMovieStrategy(final Context context) {
       return  new MovieStrategy();
    }
    
}
