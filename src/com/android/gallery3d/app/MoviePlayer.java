/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Metadata;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.Virtualizer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.VideoView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.BlobCache;
import com.android.gallery3d.util.CacheManager;
import com.android.gallery3d.util.GalleryUtils;
import org.codeaurora.gallery3d.ext.IContrllerOverlayExt;
import org.codeaurora.gallery3d.ext.IMoviePlayer;
import org.codeaurora.gallery3d.ext.IMovieItem;
import org.codeaurora.gallery3d.ext.MovieUtils;
import org.codeaurora.gallery3d.video.BookmarkEnhance;
import org.codeaurora.gallery3d.video.ExtensionHelper;
import org.codeaurora.gallery3d.video.IControllerRewindAndForward;
import org.codeaurora.gallery3d.video.IControllerRewindAndForward.IRewindAndForwardListener;
import org.codeaurora.gallery3d.video.ScreenModeManager;
import org.codeaurora.gallery3d.video.ScreenModeManager.ScreenModeListener;
import org.codeaurora.gallery3d.video.CodeauroraVideoView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.Map;

public class MoviePlayer implements
        MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener,
        ControllerOverlay.Listener,
        MediaPlayer.OnInfoListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnVideoSizeChangedListener,
        MediaPlayer.OnBufferingUpdateListener {
    @SuppressWarnings("unused")
    private static final String TAG = "MoviePlayer";
    private static final boolean LOG = false;

    private static final String KEY_VIDEO_POSITION = "video-position";
    private static final String KEY_RESUMEABLE_TIME = "resumeable-timeout";

    // These are constants in KeyEvent, appearing on API level 11.
    private static final int KEYCODE_MEDIA_PLAY = 126;
    private static final int KEYCODE_MEDIA_PAUSE = 127;

    private static final String KEY_VIDEO_CAN_SEEK = "video_can_seek";
    private static final String KEY_VIDEO_CAN_PAUSE = "video_can_pause";
    private static final String KEY_VIDEO_LAST_DURATION = "video_last_duration";
    private static final String KEY_VIDEO_LAST_DISCONNECT_TIME = "last_disconnect_time";
    private static final String KEY_VIDEO_STREAMING_TYPE = "video_streaming_type";
    private static final String KEY_VIDEO_STATE = "video_state";

    private static final String VIRTUALIZE_EXTRA = "virtualize";
    private static final long BLACK_TIMEOUT = 500;
    private static final int DELAY_REMOVE_MS = 10000;
    public static final int SERVER_TIMEOUT = 8801;

    // If we resume the acitivty with in RESUMEABLE_TIMEOUT, we will keep playing.
    // Otherwise, we pause the player.
    private static final long RESUMEABLE_TIMEOUT = 3 * 60 * 1000; // 3 mins

    public static final int STREAMING_LOCAL = 0;
    public static final int STREAMING_HTTP = 1;
    public static final int STREAMING_RTSP = 2;
    public static final int STREAMING_SDP = 3;
    private int mStreamingType = STREAMING_LOCAL;

    private Context mContext;
    private final CodeauroraVideoView mVideoView;
    private final View mRootView;
    private final Bookmarker mBookmarker;
    private final Handler mHandler = new Handler();
    private final AudioBecomingNoisyReceiver mAudioBecomingNoisyReceiver;
    private final MovieControllerOverlay mController;

    private long mResumeableTime = Long.MAX_VALUE;
    private int mVideoPosition = 0;
    private boolean mHasPaused = false;
    private boolean mVideoHasPaused = false;
    private boolean mCanResumed = false;
    private boolean mFirstBePlayed = false;
    private boolean mKeyguardLocked = false;
    private boolean mIsOnlyAudio = false;
    private int mLastSystemUiVis = 0;

    // If the time bar is being dragged.
    private boolean mDragging;

    // If the time bar is visible.
    private boolean mShowing;

    private Virtualizer mVirtualizer;

    private MovieActivity mActivityContext;//for dialog and toast context
    private MoviePlayerExtension mPlayerExt = new MoviePlayerExtension();
    private RetryExtension mRetryExt = new RetryExtension();
    private ServerTimeoutExtension mServerTimeoutExt = new ServerTimeoutExtension();
    private ScreenModeExt mScreenModeExt = new ScreenModeExt();
    private IContrllerOverlayExt mOverlayExt;
    private IControllerRewindAndForward mControllerRewindAndForwardExt;
    private IRewindAndForwardListener mRewindAndForwardListener = new ControllerRewindAndForwardExt();
    private boolean mCanReplay;
    private boolean mVideoCanSeek = false;
    private boolean mVideoCanPause = false;
    private boolean mWaitMetaData;
    private boolean mIsShowResumingDialog;
    private TState mTState = TState.PLAYING;
    private IMovieItem mMovieItem;
    private int mVideoLastDuration;//for duration displayed in init state

    private enum TState {
        PLAYING,
        PAUSED,
        STOPED,
        COMPELTED,
        RETRY_ERROR
    }

    interface Restorable {
        void onRestoreInstanceState(Bundle icicle);
        void onSaveInstanceState(Bundle outState);
    }

    private final Runnable mPlayingChecker = new Runnable() {
        @Override
        public void run() {
            if (mVideoView.isPlaying()) {
                mController.showPlaying();
            } else {
                mHandler.postDelayed(mPlayingChecker, 250);
            }
        }
    };

    private final Runnable mProgressChecker = new Runnable() {
        @Override
        public void run() {
            int pos = setProgress();
            mHandler.postDelayed(mProgressChecker, 1000 - (pos % 1000));
        }
    };

    private Runnable mDelayVideoRunnable = new Runnable() {
        @Override
        public void run() {
            if (LOG) {
                Log.v(TAG, "mDelayVideoRunnable.run()");
            }
            mVideoView.setVisibility(View.VISIBLE);
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                mKeyguardLocked = true;
            } else if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                if ((mCanResumed) && (!mVideoHasPaused)) {
                    playVideo();
                }
                mKeyguardLocked = false;
                mCanResumed = false;
            } else if (Intent.ACTION_SHUTDOWN.equals(intent.getAction())) {
                if (LOG) {
                    Log.v(TAG, "Intent.ACTION_SHUTDOWN received");
                }
                mActivityContext.finish();
            }
        }
    };

    public MoviePlayer(View rootView, final MovieActivity movieActivity,
            IMovieItem info, Bundle savedInstance, boolean canReplay) {
        mContext = movieActivity.getApplicationContext();
        mRootView = rootView;
        mVideoView = (CodeauroraVideoView) rootView.findViewById(R.id.surface_view);
        mBookmarker = new Bookmarker(movieActivity);

        mController = new MovieControllerOverlay(movieActivity);
        ((ViewGroup)rootView).addView(mController.getView());
        mController.setListener(this);
        mController.setCanReplay(canReplay);

        init(movieActivity, info, canReplay);

        mVideoView.setOnErrorListener(this);
        mVideoView.setOnCompletionListener(this);

        if (mVirtualizer != null) {
            mVirtualizer.release();
            mVirtualizer = null;
        }

        Intent ai = movieActivity.getIntent();
        boolean virtualize = ai.getBooleanExtra(VIRTUALIZE_EXTRA, false);
        if (virtualize) {
            int session = mVideoView.getAudioSessionId();
            if (session != 0) {
                mVirtualizer = new Virtualizer(0, session);
                mVirtualizer.setEnabled(true);
            } else {
                Log.w(TAG, "no audio session to virtualize");
            }
        }
        mVideoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mController.show();
                return true;
            }
        });
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer player) {
                if (!mVideoView.canSeekForward() || !mVideoView.canSeekBackward()) {
                    mController.setSeekable(false);
                } else {
                    mController.setSeekable(true);
                }
                setProgress();
            }
        });

        // The SurfaceView is transparent before drawing the first frame.
        // This makes the UI flashing when open a video. (black -> old screen
        // -> video) However, we have no way to know the timing of the first
        // frame. So, we hide the VideoView for a while to make sure the
        // video has been drawn on it.
        mVideoView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mVideoView.setVisibility(View.VISIBLE);
            }
        }, BLACK_TIMEOUT);

        setOnSystemUiVisibilityChangeListener();
        // Hide system UI by default
        showSystemUi(false);

        mAudioBecomingNoisyReceiver = new AudioBecomingNoisyReceiver();
        mAudioBecomingNoisyReceiver.register();

        // Listen for broadcasts related to user-presence
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        mContext.registerReceiver(mReceiver, filter);

        if (savedInstance != null) { // this is a resumed activity
            mVideoPosition = savedInstance.getInt(KEY_VIDEO_POSITION, 0);
            mResumeableTime = savedInstance.getLong(KEY_RESUMEABLE_TIME, Long.MAX_VALUE);
            onRestoreInstanceState(savedInstance);
            mHasPaused = true;
        } else {
            mTState = TState.PLAYING;
            mFirstBePlayed = true;
            final BookmarkerInfo bookmark = mBookmarker.getBookmark(mMovieItem.getUri());
            if (bookmark != null) {
                showResumeDialog(movieActivity, bookmark);
            } else {
                doStartVideo(false, 0, 0);
            }
        }
        mScreenModeExt.setScreenMode();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setOnSystemUiVisibilityChangeListener() {
        if (!ApiHelper.HAS_VIEW_SYSTEM_UI_FLAG_HIDE_NAVIGATION) return;

        // When the user touches the screen or uses some hard key, the framework
        // will change system ui visibility from invisible to visible. We show
        // the media control and enable system UI (e.g. ActionBar) to be visible at this point
        mVideoView.setOnSystemUiVisibilityChangeListener(
                new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                boolean finish = (mActivityContext == null ? true : mActivityContext.isFinishing());
                int diff = mLastSystemUiVis ^ visibility;
                mLastSystemUiVis = visibility;
                if ((diff & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0
                        && (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
                    mController.show();
                    mRootView.setBackgroundColor(Color.BLACK);
                }

                if (LOG) {
                    Log.v(TAG, "onSystemUiVisibilityChange(" + visibility + ") finishing()=" + finish);
                }
            }
        });
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void showSystemUi(boolean visible) {
        if (!ApiHelper.HAS_VIEW_SYSTEM_UI_FLAG_LAYOUT_STABLE) return;

        int flag = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (!visible) {
            // We used the deprecated "STATUS_BAR_HIDDEN" for unbundling
            flag |= View.STATUS_BAR_HIDDEN | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }
        mVideoView.setSystemUiVisibility(flag);
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(KEY_VIDEO_POSITION, mVideoPosition);
        outState.putLong(KEY_RESUMEABLE_TIME, mResumeableTime);
        onSaveInstanceStateMore(outState);
    }

    private void showResumeDialog(Context context, final BookmarkerInfo bookmark) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.resume_playing_title);
        builder.setMessage(String.format(
                context.getString(R.string.resume_playing_message),
                GalleryUtils.formatDuration(context, bookmark.mBookmark / 1000)));
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                onCompletion();
            }
        });
        builder.setPositiveButton(
                R.string.resume_playing_resume, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // here try to seek for bookmark
                mVideoCanSeek = true;
                doStartVideo(true, bookmark.mBookmark, bookmark.mDuration);
            }
        });
        builder.setNegativeButton(
                R.string.resume_playing_restart, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                doStartVideo(true, 0, bookmark.mDuration);
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface arg0) {
                mIsShowResumingDialog = true;
            }
        });
        dialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface arg0) {
                mIsShowResumingDialog = false;
            }
        });
        dialog.show();
    }

    public void setDefaultScreenMode() {
        addBackground();
        mController.setDefaultScreenMode();
        removeBackground();
    }

    public boolean onPause() {
        if (LOG) {
            Log.v(TAG, "onPause() isLiveStreaming()=" + isLiveStreaming());
        }
        boolean pause = false;
        if (isLiveStreaming()) {
            pause = false;
        } else {
            doOnPause();
            pause = true;
        }
        if (LOG) {
            Log.v(TAG, "onPause() , return " + pause);
        }
        return pause;
    }

    // we should stop video anyway after this function called.
    public void onStop() {
        if (LOG) {
            Log.v(TAG, "onStop() mHasPaused=" + mHasPaused);
        }
        if (!mHasPaused) {
            doOnPause();
        }
    }

    private void doOnPause() {
        long start = System.currentTimeMillis();
        addBackground();
        mHasPaused = true;
        mHandler.removeCallbacksAndMessages(null);
        int position = mVideoView.getCurrentPosition();
        mVideoPosition = position >= 0 ? position : mVideoPosition;
        Log.v(TAG, "mVideoPosition is " + mVideoPosition);
        int duration = mVideoView.getDuration();
        mVideoLastDuration = duration > 0 ? duration : mVideoLastDuration;
        mBookmarker.setBookmark(mMovieItem.getUri(), mVideoPosition, mVideoLastDuration);
        long end1 = System.currentTimeMillis();
        mVideoView.suspend();
        mResumeableTime = System.currentTimeMillis() + RESUMEABLE_TIMEOUT;
        mVideoView.setResumed(false);// avoid start after surface created
        // Workaround for last-seek frame difference
        mVideoView.setVisibility(View.INVISIBLE);
        long end2 = System.currentTimeMillis();
        // TODO comments by sunlei
        mOverlayExt.clearBuffering();
        mServerTimeoutExt.recordDisconnectTime();
        if (LOG) {
            Log.v(TAG, "doOnPause() save video info consume:" + (end1 - start));
            Log.v(TAG, "doOnPause() suspend video consume:" + (end2 - end1));
            Log.v(TAG, "doOnPause() mVideoPosition=" + mVideoPosition + ", mResumeableTime="
                    + mResumeableTime
                    + ", mVideoLastDuration=" + mVideoLastDuration + ", mIsShowResumingDialog="
                    + mIsShowResumingDialog);
        }
    }

    public void onResume() {
        mDragging = false;// clear drag info
        if (mHasPaused) {
            //M: same as launch case to delay transparent.
            mVideoView.removeCallbacks(mDelayVideoRunnable);
            mVideoView.postDelayed(mDelayVideoRunnable, BLACK_TIMEOUT);

            if (mServerTimeoutExt.handleOnResume() || mIsShowResumingDialog) {
                mHasPaused = false;
                return;
            }
            switch (mTState) {
                case RETRY_ERROR:
                    mRetryExt.showRetry();
                    break;
                case STOPED:
                    mPlayerExt.stopVideo();
                    break;
                case COMPELTED:
                    mController.showEnded();
                    if (mVideoCanSeek || mVideoView.canSeekForward()) {
                        mVideoView.seekTo(mVideoPosition);
                    }
                    mVideoView.setDuration(mVideoLastDuration);
                    break;
                case PAUSED:
                    // if video was paused, so it should be started.
                    doStartVideo(true, mVideoPosition, mVideoLastDuration, false);
                    pauseVideo();
                    break;
                default:
                    mVideoView.seekTo(mVideoPosition);
                    mVideoView.resume();
                    pauseVideoMoreThanThreeMinutes();
                    break;
            }
            mHasPaused = false;
        }

        if (System.currentTimeMillis() > mResumeableTime) {
            mHandler.removeCallbacks(mPlayingChecker);
            mHandler.postDelayed(mPlayingChecker, 250);
        }

        mHandler.post(mProgressChecker);
    }

    private void pauseVideoMoreThanThreeMinutes() {
        // If we have slept for too long, pause the play
        // If is live streaming, do not pause it too
        long now = System.currentTimeMillis();
        if (now > mResumeableTime && !isLiveStreaming()) {
            if (mVideoCanPause || mVideoView.canPause()) {
                pauseVideo();
            }
        }
        if (LOG) {
            Log.v(TAG, "pauseVideoMoreThanThreeMinutes() now=" + now);
        }
    }

    public void onDestroy() {
        if (mVirtualizer != null) {
            mVirtualizer.release();
            mVirtualizer = null;
        }
        mVideoView.stopPlayback();
        mAudioBecomingNoisyReceiver.unregister();
        mContext.unregisterReceiver(mReceiver);
        mServerTimeoutExt.clearTimeoutDialog();
    }

    // This updates the time bar display (if necessary). It is called every
    // second by mProgressChecker and also from places where the time bar needs
    // to be updated immediately.
    private int setProgress() {
        if (mDragging || (!mShowing && !mIsOnlyAudio)) {
            return 0;
        }
        int position = mVideoView.getCurrentPosition();
        int duration = mVideoView.getDuration();
        mController.setTimes(position, duration, 0, 0);
        if (mControllerRewindAndForwardExt != null
		        && mControllerRewindAndForwardExt.getPlayPauseEanbled()) {
            updateRewindAndForwardUI();
        }
        return position;
    }

    private void doStartVideo(final boolean enableFasten, final int position, final int duration,
            boolean start) {
        // For streams that we expect to be slow to start up, show a
        // progress spinner until playback starts.
        String scheme = mMovieItem.getUri().getScheme();
        if ("http".equalsIgnoreCase(scheme) || "rtsp".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme)) {
            mController.showLoading();
            mOverlayExt.setPlayingInfo(isLiveStreaming());
            mHandler.removeCallbacks(mPlayingChecker);
            mHandler.postDelayed(mPlayingChecker, 250);
        } else {
            mController.showPlaying();
            mController.hide();
        }

        if (onIsRTSP()) {
            Map<String, String> header = new HashMap<String, String>(1);
            header.put("CODEAURORA-ASYNC-RTSP-PAUSE-PLAY", "true");
            mVideoView.setVideoURI(mMovieItem.getUri(), header, !mWaitMetaData);
        } else {
            mVideoView.setVideoURI(mMovieItem.getUri(), null, !mWaitMetaData);
        }
        if (start) {
            mVideoView.start();
            mVideoView.setVisibility(View.VISIBLE);
            mActivityContext.initEffects(mVideoView.getAudioSessionId());
        }
        //we may start video from stopVideo,
        //this case, we should reset canReplay flag according canReplay and loop
        boolean loop = mPlayerExt.getLoop();
        boolean canReplay = loop ? loop : mCanReplay;
        mController.setCanReplay(canReplay);
        if (position > 0 && (mVideoCanSeek || mVideoView.canSeek())) {
            mVideoView.seekTo(position);
        }
        if (enableFasten) {
            mVideoView.setDuration(duration);
        }
        setProgress();
    }

    private void doStartVideo(boolean enableFasten, int position, int duration) {
        doStartVideo(enableFasten, position, duration, true);
    }

    private void playVideo() {
        if (LOG) {
            Log.v(TAG, "playVideo()");
        }
        mTState = TState.PLAYING;
        mVideoView.start();
        mController.showPlaying();
        setProgress();
    }

    private void pauseVideo() {
        if (LOG) {
            Log.v(TAG, "pauseVideo()");
        }
        mTState = TState.PAUSED;
        mVideoView.pause();
        mController.showPaused();
    }

    // Below are notifications from VideoView
    @Override
    public boolean onError(MediaPlayer player, int arg1, int arg2) {
        mMovieItem.setError();
        if (mServerTimeoutExt.onError(player, arg1, arg2)) {
            return true;
        }
        if (mRetryExt.onError(player, arg1, arg2)) {
            return true;
        }
        mHandler.removeCallbacksAndMessages(null);
        // VideoView will show an error dialog if we return false, so no need
        // to show more message.
        //M:resume controller
        mController.setViewEnabled(true);
        mController.showErrorMessage("");
        return false;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (LOG) {
            Log.v(TAG, "onCompletion() mCanReplay=" + mCanReplay);
        }
        if (mMovieItem.getError()) {
            Log.w(TAG, "error occured, exit the video player!");
            mActivityContext.finish();
            return;
        }
        if (mPlayerExt.getLoop()) {
            onReplay();
        } else { //original logic
            mTState = TState.COMPELTED;
            if (mCanReplay) {
                mController.showEnded();
            }
            onCompletion();
        }
    }

    public void onCompletion() {
    }

    // Below are notifications from ControllerOverlay
    @Override
    public void onPlayPause() {
        if (mVideoView.isPlaying()) {
            if (mVideoView.canPause()) {
                pauseVideo();
                //set view disabled(play/pause asynchronous processing)
                mController.setViewEnabled(true);
                if (mControllerRewindAndForwardExt != null) {
                    mControllerRewindAndForwardExt.showControllerButtonsView(mPlayerExt
                            .canStop(), false, false);
                }
            }
        } else {
            playVideo();
            //set view disabled(play/pause asynchronous processing)
            mController.setViewEnabled(true);
            if (mControllerRewindAndForwardExt != null) {
                mControllerRewindAndForwardExt.showControllerButtonsView(mPlayerExt
                        .canStop(), false, false);
            }
        }
    }

    @Override
    public void onSeekStart() {
        mDragging = true;
    }

    @Override
    public void onSeekMove(int time) {
        if (mVideoView.canSeek()) {
            mVideoView.seekTo(time);
        }
    }

    @Override
    public void onSeekEnd(int time, int start, int end) {
        mDragging = false;
        if (mVideoView.canSeek()) {
            mVideoView.seekTo(time);
        }
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        setProgress();
    }

    @Override
    public void onShown() {
        addBackground();
        mShowing = true;
        setProgress();
        showSystemUi(true);
    }

    @Override
    public void onHidden() {
        mShowing = false;
        showSystemUi(false);
        removeBackground();
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (LOG) {
            Log.v(TAG, "onInfo() what:" + what + " extra:" + extra);
        }
        if (what == MediaPlayer.MEDIA_INFO_NOT_SEEKABLE && mOverlayExt != null) {
            boolean flag = (extra == 1);
            mOverlayExt.setCanPause(flag);
            mOverlayExt.setCanScrubbing(flag);
        } else if (what == MediaPlayer.MEDIA_INFO_METADATA_UPDATE && mServerTimeoutExt != null) {
            Log.e(TAG, "setServerTimeout " + extra);
            mServerTimeoutExt.setTimeout(extra * 1000);
        }
        if (mRetryExt.onInfo(mp, what, extra)) {
            return true;
        }
        return false;
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        boolean fullBuffer = isFullBuffer();
        mOverlayExt.showBuffering(fullBuffer, percent);
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (LOG) {
            Log.v(TAG, "onPrepared(" + mp + ")");
        }
        if (!isLocalFile()) {
            mOverlayExt.setPlayingInfo(isLiveStreaming());
        }
        getVideoInfo(mp);
        boolean canPause = mVideoView.canPause();
        boolean canSeek = mVideoView.canSeek();
        mOverlayExt.setCanPause(canPause);
        mOverlayExt.setCanScrubbing(canSeek);
        mController.setPlayPauseReplayResume();
        if (!canPause && !mVideoView.isTargetPlaying()) {
            mVideoView.start();
        }
        updateRewindAndForwardUI();
        if (LOG) {
            Log.v(TAG, "onPrepared() canPause=" + canPause + ", canSeek=" + canSeek);
        }
    }

    public boolean onIsRTSP() {
        if (MovieUtils.isRtspStreaming(mMovieItem.getUri(), mMovieItem
                .getMimeType())) {
            if (LOG) {
                Log.v(TAG, "onIsRTSP() is RTSP");
            }
            return true;
        }
        if (LOG) {
            Log.v(TAG, "onIsRTSP() is not RTSP");
        }
        return false;
    }

    @Override
    public void onReplay() {
        if (LOG) {
            Log.v(TAG, "onReplay()");
        }
        mTState = TState.PLAYING;
        mFirstBePlayed = true;
        if (mRetryExt.handleOnReplay()) {
            return;
        }
        doStartVideo(false, 0, 0);
    }

    // Below are key events passed from MovieActivity.
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        // Some headsets will fire off 7-10 events on a single click
        if (event.getRepeatCount() > 0) {
            return isMediaKey(keyCode);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (mVideoView.isPlaying() && mVideoView.canPause()) {
                    pauseVideo();
                } else {
                    playVideo();
                }
                return true;
            case KEYCODE_MEDIA_PAUSE:
                if (mVideoView.isPlaying() && mVideoView.canPause()) {
                    pauseVideo();
                }
                return true;
            case KEYCODE_MEDIA_PLAY:
                if (!mVideoView.isPlaying()) {
                    playVideo();
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                // TODO: Handle next / previous accordingly, for now we're
                // just consuming the events.
                return true;
        }
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return isMediaKey(keyCode);
    }

    public void updateRewindAndForwardUI() {
        if (LOG) {
            Log.v(TAG, "updateRewindAndForwardUI");
            Log.v(TAG, "updateRewindAndForwardUI== getCurrentPosition = " +  mVideoView.getCurrentPosition());
            Log.v(TAG, "updateRewindAndForwardUI==getDuration =" +  mVideoView.getDuration());
        }
        if (mControllerRewindAndForwardExt != null) {
            mControllerRewindAndForwardExt.showControllerButtonsView(mPlayerExt
                    .canStop(), mVideoView.canSeekBackward()
                    && mControllerRewindAndForwardExt.getTimeBarEanbled(), mVideoView
                    .canSeekForward()
                    && mControllerRewindAndForwardExt.getTimeBarEanbled());
        }
    }

    private static boolean isMediaKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE;
    }

    private void init(MovieActivity movieActivity, IMovieItem info, boolean canReplay) {
        mActivityContext = movieActivity;
        mCanReplay = canReplay;
        mMovieItem = info;
        judgeStreamingType(info.getUri(), info.getMimeType());

        mVideoView.setOnInfoListener(this);
        mVideoView.setOnPreparedListener(this);
        mVideoView.setOnBufferingUpdateListener(this);
        mVideoView.setOnVideoSizeChangedListener(this);
        mRootView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mController.show();
                return true;
            }
        });
        mOverlayExt = mController.getOverlayExt();
        mControllerRewindAndForwardExt = mController.getControllerRewindAndForwardExt();
        if (mControllerRewindAndForwardExt != null) {
            mControllerRewindAndForwardExt.setIListener(mRewindAndForwardListener);
        }
    }

    // We want to pause when the headset is unplugged.
    private class AudioBecomingNoisyReceiver extends BroadcastReceiver {

        public void register() {
            mContext.registerReceiver(this,
                    new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        }

        public void unregister() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mVideoView.isPlaying() && mVideoView.canPause()) pauseVideo();
        }
    }

    public int getAudioSessionId() {
        return mVideoView.getAudioSessionId();
    }

    public void setOnPreparedListener(MediaPlayer.OnPreparedListener listener) {
        mVideoView.setOnPreparedListener(listener);
    }

    public boolean isFullBuffer() {
        if (mStreamingType == STREAMING_RTSP || mStreamingType == STREAMING_SDP) {
            return false;
        }
        return true;
    }

    public boolean isLocalFile() {
        if (mStreamingType == STREAMING_LOCAL) {
            return true;
        }
        return false;
    }

    private void getVideoInfo(MediaPlayer mp) {
        if (!MovieUtils.isLocalFile(mMovieItem.getUri(), mMovieItem.getMimeType())) {
            Metadata data = mp.getMetadata(MediaPlayer.METADATA_ALL,
                    MediaPlayer.BYPASS_METADATA_FILTER);
            if (data != null) {
                // TODO comments by sunlei
                mServerTimeoutExt.setVideoInfo(data);
            } else {
                Log.w(TAG, "Metadata is null!");
            }
        }
    }

    private void judgeStreamingType(Uri uri, String mimeType) {
        if (LOG) {
            Log.v(TAG, "judgeStreamingType(" + uri + ")");
        }
        if (uri == null) {
            return;
        }
        String scheme = uri.getScheme();
        mWaitMetaData = true;
        if (MovieUtils.isSdpStreaming(uri, mimeType)) {
            mStreamingType = STREAMING_SDP;
            mWaitMetaData = false;
        } else if (MovieUtils.isRtspStreaming(uri, mimeType)) {
            mStreamingType = STREAMING_RTSP;
            mWaitMetaData = false;
        } else if (MovieUtils.isHttpStreaming(uri, mimeType)) {
            mStreamingType = STREAMING_HTTP;
            mWaitMetaData = false;
        } else {
            mStreamingType = STREAMING_LOCAL;
            mWaitMetaData = false;
        }
        if (LOG) {
            Log.v(TAG, "mStreamingType=" + mStreamingType
                    + " mCanGetMetaData=" + mWaitMetaData);
        }
    }

    public boolean isLiveStreaming() {
        boolean isLive = false;
        if (mStreamingType == STREAMING_SDP) {
            isLive = true;
        }
        if (LOG) {
            Log.v(TAG, "isLiveStreaming() return " + isLive);
        }
        return isLive;
    }

    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        // reget the audio type
        if (width != 0 && height != 0) {
            mIsOnlyAudio = false;
        } else {
            mIsOnlyAudio = true;
        }
        mOverlayExt.setBottomPanel(mIsOnlyAudio, true);
        if (LOG) {
            Log.v(TAG, "onVideoSizeChanged(" + width + ", " + height + ") mIsOnlyAudio="
                    + mIsOnlyAudio);
        }
    }

    public IMoviePlayer getMoviePlayerExt() {
        return mPlayerExt;
    }

    public SurfaceView getVideoSurface() {
        return mVideoView;
    }

    // Wait for any animation, ten seconds should be enough
    private final Runnable mRemoveBackground = new Runnable() {
        @Override
        public void run() {
            if (LOG) {
                Log.v(TAG, "mRemoveBackground.run()");
            }
            mRootView.setBackground(null);
        }
    };

    private void removeBackground() {
        if (LOG) {
            Log.v(TAG, "removeBackground()");
        }
        mHandler.removeCallbacks(mRemoveBackground);
        mHandler.postDelayed(mRemoveBackground, DELAY_REMOVE_MS);
    }

    // add background for removing ghost image.
    private void addBackground() {
        if (LOG) {
            Log.v(TAG, "addBackground()");
        }
        mHandler.removeCallbacks(mRemoveBackground);
        mRootView.setBackgroundColor(Color.BLACK);
    }

    private void clearVideoInfo() {
        mVideoPosition = 0;
        mVideoLastDuration = 0;
        mIsOnlyAudio = false;

        if (mServerTimeoutExt != null) {
            mServerTimeoutExt.clearServerInfo();
        }
    }

    private void onSaveInstanceStateMore(Bundle outState) {
        outState.putInt(KEY_VIDEO_LAST_DURATION, mVideoLastDuration);
        outState.putBoolean(KEY_VIDEO_CAN_SEEK, mVideoView.canSeekForward());
        outState.putBoolean(KEY_VIDEO_CAN_PAUSE, mVideoView.canPause());
        outState.putInt(KEY_VIDEO_STREAMING_TYPE, mStreamingType);
        outState.putString(KEY_VIDEO_STATE, String.valueOf(mTState));
        mServerTimeoutExt.onSaveInstanceState(outState);
        mScreenModeExt.onSaveInstanceState(outState);
        mRetryExt.onSaveInstanceState(outState);
        mPlayerExt.onSaveInstanceState(outState);
    }

    private void onRestoreInstanceState(Bundle icicle) {
        mVideoLastDuration = icicle.getInt(KEY_VIDEO_LAST_DURATION);
        mVideoCanSeek = icicle.getBoolean(KEY_VIDEO_CAN_SEEK);
        mVideoCanPause = icicle.getBoolean(KEY_VIDEO_CAN_PAUSE);
        mStreamingType = icicle.getInt(KEY_VIDEO_STREAMING_TYPE);
        mTState = TState.valueOf(icicle.getString(KEY_VIDEO_STATE));
        mServerTimeoutExt.onRestoreInstanceState(icicle);
        mScreenModeExt.onRestoreInstanceState(icicle);
        mRetryExt.onRestoreInstanceState(icicle);
        mPlayerExt.onRestoreInstanceState(icicle);
    }

    private class MoviePlayerExtension implements IMoviePlayer, Restorable {

        private static final String KEY_VIDEO_IS_LOOP = "video_is_loop";

        private BookmarkEnhance mBookmark;//for bookmark
        private boolean mIsLoop;
        private boolean mLastPlaying;
        private boolean mLastCanPaused;

        @Override
        public boolean getLoop() {
            return mIsLoop;
        }

        @Override
        public void setLoop(boolean loop) {
            if (isLocalFile()) {
                mIsLoop = loop;
                mController.setCanReplay(loop);
            }
        }

        @Override
        public void startNextVideo(IMovieItem item) {
            IMovieItem next = item;
            if (next != null && next != mMovieItem) {
                int position = mVideoView.getCurrentPosition();
                int duration = mVideoView.getDuration();
                mBookmarker.setBookmark(mMovieItem.getUri(), position, duration);
                mVideoView.stopPlayback();
                mVideoView.setVisibility(View.INVISIBLE);
                clearVideoInfo();
                mActivityContext.releaseEffects();
                mMovieItem = next;
                mActivityContext.refreshMovieInfo(mMovieItem);
                doStartVideo(false, 0, 0);
                mVideoView.setVisibility(View.VISIBLE);
            } else {
                Log.e(TAG, "Cannot play the next video! " + item);
            }
            mActivityContext.closeOptionsMenu();
        }

        @Override
        public void onRestoreInstanceState(Bundle icicle) {
            mIsLoop = icicle.getBoolean(KEY_VIDEO_IS_LOOP, false);
            if (mIsLoop) {
                mController.setCanReplay(true);
            } // else  will get can replay from intent.
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            outState.putBoolean(KEY_VIDEO_IS_LOOP, mIsLoop);
        }

        @Override
        public void stopVideo() {
            if (LOG) {
                Log.v(TAG, "stopVideo()");
            }
            mTState = TState.STOPED;
            mVideoView.clearSeek();
            mVideoView.clearDuration();
            mVideoView.stopPlayback();
            mVideoView.setResumed(false);
            mVideoView.setVisibility(View.INVISIBLE);
            clearVideoInfo();
            mActivityContext.releaseEffects();
            mFirstBePlayed = false;
            mController.setCanReplay(true);
            mController.showEnded();
            mController.setViewEnabled(true);
            setProgress();
        }

        @Override
        public boolean canStop() {
            boolean stopped = false;
            if (mController != null) {
                stopped = mOverlayExt.isPlayingEnd();
            }
            if (LOG) {
                Log.v(TAG, "canStop() stopped=" + stopped);
            }
            return !stopped;
        }

        @Override
        public void addBookmark() {
            if (mBookmark == null) {
                mBookmark = new BookmarkEnhance(mActivityContext);
            }
            String uri = String.valueOf(mMovieItem.getUri());
            if (mBookmark.exists(uri)) {
                Toast.makeText(mActivityContext, R.string.bookmark_exist, Toast.LENGTH_SHORT)
                        .show();
            } else {
                mBookmark.insert(mMovieItem.getTitle(), uri,
                        mMovieItem.getMimeType(), 0);
                Toast.makeText(mActivityContext, R.string.bookmark_add_success, Toast.LENGTH_SHORT)
                        .show();
            }
        }
    };

    private class RetryExtension implements Restorable, MediaPlayer.OnErrorListener,
            MediaPlayer.OnInfoListener {
        private static final String KEY_VIDEO_RETRY_COUNT = "video_retry_count";
        private int mRetryDuration;
        private int mRetryPosition;
        private int mRetryCount;

        public void retry() {
            doStartVideo(true, mRetryPosition, mRetryDuration);
            if (LOG) {
                Log.v(TAG, "retry() mRetryCount=" + mRetryCount + ", mRetryPosition="
                        + mRetryPosition);
            }
        }

        public void clearRetry() {
            if (LOG) {
                Log.v(TAG, "clearRetry() mRetryCount=" + mRetryCount);
            }
            mRetryCount = 0;
        }

        public boolean reachRetryCount() {
            if (LOG) {
                Log.v(TAG, "reachRetryCount() mRetryCount=" + mRetryCount);
            }
            if (mRetryCount > 3) {
                return true;
            }
            return false;
        }

        public int getRetryCount() {
            if (LOG) {
                Log.v(TAG, "getRetryCount() return " + mRetryCount);
            }
            return mRetryCount;
        }

        public boolean isRetrying() {
            boolean retry = false;
            if (mRetryCount > 0) {
                retry = true;
            }
            if (LOG) {
                Log.v(TAG, "isRetrying() mRetryCount=" + mRetryCount);
            }
            return retry;
        }

        @Override
        public void onRestoreInstanceState(Bundle icicle) {
            mRetryCount = icicle.getInt(KEY_VIDEO_RETRY_COUNT);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            outState.putInt(KEY_VIDEO_RETRY_COUNT, mRetryCount);
        }

        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            return false;
        }

        @Override
        public boolean onInfo(MediaPlayer mp, int what, int extra) {
            return false;
        }

        public boolean handleOnReplay() {
            if (isRetrying()) { // from connecting error
                clearRetry();
                int errorPosition = mVideoView.getCurrentPosition();
                int errorDuration = mVideoView.getDuration();
                doStartVideo(errorPosition > 0, errorPosition, errorDuration);
                if (LOG) {
                    Log.v(TAG, "onReplay() errorPosition=" + errorPosition + ", errorDuration="
                            + errorDuration);
                }
                return true;
            }
            return false;
        }

        public void showRetry() {
            if (mVideoCanSeek || mVideoView.canSeekForward()) {
                mVideoView.seekTo(mVideoPosition);
            }
            mVideoView.setDuration(mVideoLastDuration);
            mRetryPosition = mVideoPosition;
            mRetryDuration = mVideoLastDuration;
        }
    }
    private class ServerTimeoutExtension implements Restorable, MediaPlayer.OnErrorListener {
        // for cmcc server timeout case
        // please remember to clear this value when changed video.
        private int mServerTimeout = -1;
        private long mLastDisconnectTime;
        private boolean mIsShowDialog = false;
        private AlertDialog mServerTimeoutDialog;
        private int RESUME_DIALOG_TIMEOUT = 3 * 60 * 1000; // 3 mins

        // check whether disconnect from server timeout or not.
        // if timeout, return false. otherwise, return true.
        private boolean passDisconnectCheck() {
            if (!isFullBuffer()) {
                // record the time disconnect from server
                long now = System.currentTimeMillis();
                if (LOG) {
                    Log.v(TAG, "passDisconnectCheck() now=" + now + ", mLastDisconnectTime="
                            + mLastDisconnectTime
                            + ", mServerTimeout=" + mServerTimeout);
                }
                if (mServerTimeout > 0 && (now - mLastDisconnectTime) > mServerTimeout) {
                    // disconnect time more than server timeout, notify user
                    notifyServerTimeout();
                    return false;
                }
            }
            return true;
        }

        private void recordDisconnectTime() {
            if (!isFullBuffer()) {
                // record the time disconnect from server
                mLastDisconnectTime = System.currentTimeMillis();
            }
            if (LOG) {
                Log.v(TAG, "recordDisconnectTime() mLastDisconnectTime=" + mLastDisconnectTime);
            }
        }

        private void clearServerInfo() {
            mServerTimeout = -1;
        }

        private void notifyServerTimeout() {
            if (mServerTimeoutDialog == null) {
                // for updating last position and duration.
                if (mVideoCanSeek || mVideoView.canSeekForward()) {
                    mVideoView.seekTo(mVideoPosition);
                }
                mVideoView.setDuration(mVideoLastDuration);
                AlertDialog.Builder builder = new AlertDialog.Builder(mActivityContext);
                mServerTimeoutDialog = builder.setTitle(R.string.server_timeout_title)
                        .setMessage(R.string.server_timeout_message)
                        .setNegativeButton(android.R.string.cancel, new OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                if (LOG) {
                                    Log.v(TAG, "NegativeButton.onClick() mIsShowDialog="
                                            + mIsShowDialog);
                                }
                                mController.showEnded();
                                onCompletion();
                            }

                        })
                        .setPositiveButton(R.string.resume_playing_resume, new OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                if (LOG) {
                                    Log.v(TAG, "PositiveButton.onClick() mIsShowDialog="
                                            + mIsShowDialog);
                                }
                                doStartVideo(true, mVideoPosition, mVideoLastDuration);
                            }

                        })
                        .create();
                mServerTimeoutDialog.setOnDismissListener(new OnDismissListener() {

                    public void onDismiss(DialogInterface dialog) {
                        if (LOG) {
                            Log.v(TAG, "mServerTimeoutDialog.onDismiss()");
                        }
                        mIsShowDialog = false;
                    }

                });
                mServerTimeoutDialog.setOnShowListener(new OnShowListener() {

                    public void onShow(DialogInterface dialog) {
                        if (LOG) {
                            Log.v(TAG, "mServerTimeoutDialog.onShow()");
                        }
                        mIsShowDialog = true;
                    }

                });
            }
            mServerTimeoutDialog.show();
        }

        private void clearTimeoutDialog() {
            if (mServerTimeoutDialog != null && mServerTimeoutDialog.isShowing()) {
                mServerTimeoutDialog.dismiss();
            }
            mServerTimeoutDialog = null;
        }

        @Override
        public void onRestoreInstanceState(Bundle icicle) {
            mLastDisconnectTime = icicle.getLong(KEY_VIDEO_LAST_DISCONNECT_TIME);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            outState.putLong(KEY_VIDEO_LAST_DISCONNECT_TIME, mLastDisconnectTime);
        }

        public boolean handleOnResume() {
            if (mIsShowDialog && !isLiveStreaming()) {
                // wait for user's operation
                return true;
            }
            if (!passDisconnectCheck()) {
                return true;
            }
            return false;
        }

        public void setVideoInfo(Metadata data) {
            mServerTimeout = RESUME_DIALOG_TIMEOUT;
            if (data.has(SERVER_TIMEOUT)) {
                mServerTimeout = data.getInt(SERVER_TIMEOUT);
                if (mServerTimeout == 0) {
                    mServerTimeout = RESUME_DIALOG_TIMEOUT;
                }
                if (LOG) {
                    Log.v(TAG, "get server timeout from metadata. mServerTimeout="
                            + mServerTimeout);
                }
            }
        }

        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            // if we are showing a dialog, cancel the error dialog
            if (mIsShowDialog) {
                return true;
            }
            return false;
        }

        public void setTimeout(int timeout) {
            mServerTimeout = timeout;
        }
    }

    private class ScreenModeExt implements Restorable, ScreenModeListener {
        private static final String KEY_VIDEO_SCREEN_MODE = "video_screen_mode";
        private int mScreenMode = ScreenModeManager.SCREENMODE_BIGSCREEN;
        private ScreenModeManager mScreenModeManager = new ScreenModeManager();

        public void setScreenMode() {
            mVideoView.setScreenModeManager(mScreenModeManager);
            mController.setScreenModeManager(mScreenModeManager);
            mScreenModeManager.addListener(this);
            //notify all listener to change screen mode
            mScreenModeManager.setScreenMode(mScreenMode);
            if (LOG) {
                Log.v(TAG, "setScreenMode() mScreenMode=" + mScreenMode);
            }
        }

        @Override
        public void onScreenModeChanged(int newMode) {
            mScreenMode = newMode;// changed from controller
            if (LOG) {
                Log.v(TAG, "OnScreenModeClicked(" + newMode + ")");
            }
        }

        @Override
        public void onRestoreInstanceState(Bundle icicle) {
            mScreenMode = icicle.getInt(KEY_VIDEO_SCREEN_MODE,
                    ScreenModeManager.SCREENMODE_BIGSCREEN);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            outState.putInt(KEY_VIDEO_SCREEN_MODE, mScreenMode);
        }
    }

    private class ControllerRewindAndForwardExt implements IRewindAndForwardListener {
        @Override
        public void onPlayPause() {
            onPlayPause();
        }

        @Override
        public void onSeekStart() {
            onSeekStart();
        }

        @Override
        public void onSeekMove(int time) {
            onSeekMove(time);
        }

        @Override
        public void onSeekEnd(int time, int trimStartTime, int trimEndTime) {
            onSeekEnd(time, trimStartTime, trimEndTime);
        }

        @Override
        public void onShown() {
            onShown();
        }

        @Override
        public void onHidden() {
            onHidden();
        }

        @Override
        public void onReplay() {
            onReplay();
        }

        @Override
        public boolean onIsRTSP() {
            return false;
        }

        @Override
        public void onStopVideo() {
            if (LOG) {
                Log.v(TAG, "ControllerRewindAndForwardExt onStopVideo()");
            }
            if (mPlayerExt.canStop()) {
                mPlayerExt.stopVideo();
                mControllerRewindAndForwardExt.showControllerButtonsView(false,
                        false, false);
            }
        }

        @Override
        public void onRewind() {
            if (LOG) {
                Log.v(TAG, "ControllerRewindAndForwardExt onRewind()");
            }
            if (mVideoView != null && mVideoView.canSeekBackward()) {
                mControllerRewindAndForwardExt.showControllerButtonsView(mPlayerExt
                        .canStop(),
                        false, false);
                int stepValue = getStepOptionValue();
                int targetDuration = mVideoView.getCurrentPosition()
                        - stepValue < 0 ? 0 : mVideoView.getCurrentPosition()
                        - stepValue;
                if (LOG) {
                    Log.v(TAG, "onRewind targetDuration " + targetDuration);
                }
                mVideoView.seekTo(targetDuration);
            } else {
                mControllerRewindAndForwardExt.showControllerButtonsView(mPlayerExt
                        .canStop(),
                        false, false);
            }
        }

        @Override
        public void onForward() {
            if (LOG) {
                Log.v(TAG, "ControllerRewindAndForwardExt onForward()");
            }
            if (mVideoView != null && mVideoView.canSeekForward()) {
                mControllerRewindAndForwardExt.showControllerButtonsView(mPlayerExt
                        .canStop(),
                        false, false);
                int stepValue = getStepOptionValue();
                int targetDuration = mVideoView.getCurrentPosition()
                        + stepValue > mVideoView.getDuration() ? mVideoView
                        .getDuration() : mVideoView.getCurrentPosition()
                        + stepValue;
                if (LOG) {
                    Log.v(TAG, "onForward targetDuration " + targetDuration);
                }
                mVideoView.seekTo(targetDuration);
            } else {
                mControllerRewindAndForwardExt.showControllerButtonsView(mPlayerExt
                        .canStop(),
                        false, false);
            }
        }
    }

    public int getStepOptionValue() {
        final String slectedStepOption = "selected_step_option";
        final String videoPlayerData = "video_player_data";
        final int stepBase = 3000;
        final int stepOptionThreeSeconds = 0;
        SharedPreferences mPrefs = mContext.getSharedPreferences(
                videoPlayerData, 0);
        return (mPrefs.getInt(slectedStepOption, stepOptionThreeSeconds) + 1) * stepBase;
    }
}

class Bookmarker {
    private static final String TAG = "Bookmarker";

    private static final String BOOKMARK_CACHE_FILE = "bookmark";
    private static final int BOOKMARK_CACHE_MAX_ENTRIES = 100;
    private static final int BOOKMARK_CACHE_MAX_BYTES = 10 * 1024;
    private static final int BOOKMARK_CACHE_VERSION = 1;

    private static final int HALF_MINUTE = 30 * 1000;
    private static final int TWO_MINUTES = 4 * HALF_MINUTE;

    private final Context mContext;

    public Bookmarker(Context context) {
        mContext = context;
    }

    public void setBookmark(Uri uri, int bookmark, int duration) {
        try {
            // do not record or override bookmark if duration is not valid.
            if (duration <= 0) {
                return;
            }
            BlobCache cache = CacheManager.getCache(mContext,
                    BOOKMARK_CACHE_FILE, BOOKMARK_CACHE_MAX_ENTRIES,
                    BOOKMARK_CACHE_MAX_BYTES, BOOKMARK_CACHE_VERSION);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeUTF(uri.toString());
            dos.writeInt(bookmark);
            dos.writeInt(Math.abs(duration));
            dos.flush();
            cache.insert(uri.hashCode(), bos.toByteArray());
        } catch (Throwable t) {
            Log.w(TAG, "setBookmark failed", t);
        }
    }

    public BookmarkerInfo getBookmark(Uri uri) {
        try {
            BlobCache cache = CacheManager.getCache(mContext,
                    BOOKMARK_CACHE_FILE, BOOKMARK_CACHE_MAX_ENTRIES,
                    BOOKMARK_CACHE_MAX_BYTES, BOOKMARK_CACHE_VERSION);

            byte[] data = cache.lookup(uri.hashCode());
            if (data == null) return null;

            DataInputStream dis = new DataInputStream(
                    new ByteArrayInputStream(data));

            String uriString = DataInputStream.readUTF(dis);
            int bookmark = dis.readInt();
            int duration = dis.readInt();

            if (!uriString.equals(uri.toString())) {
                return null;
            }

            if ((bookmark < HALF_MINUTE) || (duration < TWO_MINUTES)
                    || (bookmark > (duration - HALF_MINUTE))) {
                return null;
            }
            return new BookmarkerInfo(bookmark, duration);
        } catch (Throwable t) {
            Log.w(TAG, "getBookmark failed", t);
        }
        return null;
    }
}

class BookmarkerInfo {
    public final int mBookmark;
    public final int mDuration;

    public BookmarkerInfo(int bookmark, int duration) {
        this.mBookmark = bookmark;
        this.mDuration = duration;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("BookmarkInfo(bookmark=")
                .append(mBookmark)
                .append(", duration=")
                .append(mDuration)
                .append(")")
                .toString();
    }
}
