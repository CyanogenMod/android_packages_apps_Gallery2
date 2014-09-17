package org.codeaurora.gallery3d.video;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.media.Metadata;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.MediaController;
import android.widget.MediaController.MediaPlayerControl;

import org.codeaurora.gallery3d.video.ScreenModeManager.ScreenModeListener;

import java.io.IOException;
import java.util.Map;

/**
 * Displays a video file.  The VideoView class
 * can load images from various sources (such as resources or content
 * providers), takes care of computing its measurement from the video so that
 * it can be used in any layout manager, and provides various display options
 * such as scaling and tinting.
 */
public class CodeauroraVideoView extends SurfaceView implements MediaPlayerControl, ScreenModeListener{
    private static final boolean LOG = false;
    private String TAG = "CodeauroraVideoView";
    // settable by the client
    private Uri         mUri;
    private Map<String, String> mHeaders;

    // all possible internal states
    private static final int STATE_ERROR              = -1;
    private static final int STATE_IDLE               = 0;
    private static final int STATE_PREPARING          = 1;
    private static final int STATE_PREPARED           = 2;
    private static final int STATE_PLAYING            = 3;
    private static final int STATE_PAUSED             = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;
    private static final int STATE_SUSPENDED          = 6;
    private static final int MSG_LAYOUT_READY = 1;

    // mCurrentState is a VideoView object's current state.
    // mTargetState is the state that a method caller intends to reach.
    // For instance, regardless the VideoView object's current state,
    // calling pause() intends to bring the object to a target state
    // of STATE_PAUSED.
    private int mCurrentState = STATE_IDLE;
    private int mTargetState  = STATE_IDLE;

    // All the stuff we need for playing and showing a video
    private SurfaceHolder mSurfaceHolder = null;
    private MediaPlayer mMediaPlayer = null;
    private int         mAudioSession;
    private int         mVideoWidth;
    private int         mVideoHeight;
    private int         mSurfaceWidth;
    private int         mSurfaceHeight;
    private int         mDuration;
    private MediaController mMediaController;
    private OnCompletionListener mOnCompletionListener;
    private MediaPlayer.OnPreparedListener mOnPreparedListener;
    private MediaPlayer.OnBufferingUpdateListener mOnBufferingUpdateListener;
    private MediaPlayer.OnVideoSizeChangedListener mVideoSizeListener;
    private MediaPlayer.OnPreparedListener mPreparedListener;
    private ScreenModeManager mScreenManager;
    private int         mCurrentBufferPercentage;
    private OnErrorListener mOnErrorListener;
    private OnInfoListener  mOnInfoListener;
    private int         mSeekWhenPrepared;  // recording the seek position while preparing
    private boolean     mCanPause;
    private boolean     mCanSeekBack;
    private boolean     mCanSeekForward;
    private boolean     mCanSeek;
    private boolean     mHasGotPreparedCallBack = false;
    private boolean mNeedWaitLayout = false;
    private boolean mHasGotMetaData = false;
    private boolean mOnResumed;

    private final Handler mHandler = new Handler() {
        public void handleMessage(final Message msg) {
            if (LOG) {
                Log.v(TAG, "handleMessage() to do prepare. msg=" + msg);
            }
            switch (msg.what) {
                case MSG_LAYOUT_READY:
                    if (mMediaPlayer == null || mUri == null) {
                        Log.w(TAG, "Cannot prepare play! mMediaPlayer=" + mMediaPlayer
                                + ", mUri=" + mUri);
                        return;
                    }
                    doPreparedIfReady(mMediaPlayer);
                    break;
                default:
                    Log.w(TAG, "Unhandled message " + msg);
                    break;
            }
        }
    };

    public CodeauroraVideoView(Context context) {
        super(context);
        initVideoView();
        initialize();
    }

    public CodeauroraVideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        initVideoView();
        initialize();
    }

    public CodeauroraVideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initVideoView();
        initialize();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = 0;
        int height = 0;
        int screenMode = ScreenModeManager.SCREENMODE_BIGSCREEN;
        if (mScreenManager != null) {
            screenMode = mScreenManager.getScreenMode();
        }
        switch (screenMode) {
            case ScreenModeManager.SCREENMODE_BIGSCREEN:
                width = getDefaultSize(mVideoWidth, widthMeasureSpec);
                height = getDefaultSize(mVideoHeight, heightMeasureSpec);
                if (mVideoWidth > 0 && mVideoHeight > 0) {
                    if (mVideoWidth * height > width * mVideoHeight) {
                        height = width * mVideoHeight / mVideoWidth;
                    } else if (mVideoWidth * height < width * mVideoHeight) {
                        width = height * mVideoWidth / mVideoHeight;
                    }
                }
                break;
            case ScreenModeManager.SCREENMODE_FULLSCREEN:
                width = getDefaultSize(mVideoWidth, widthMeasureSpec);
                height = getDefaultSize(mVideoHeight, heightMeasureSpec);
                break;
            case ScreenModeManager.SCREENMODE_CROPSCREEN:
                width = getDefaultSize(mVideoWidth, widthMeasureSpec);
                height = getDefaultSize(mVideoHeight, heightMeasureSpec);
                if (mVideoWidth > 0 && mVideoHeight > 0) {
                    if (mVideoWidth * height > width * mVideoHeight) {
                        width = height * mVideoWidth / mVideoHeight;
                    } else if (mVideoWidth * height < width * mVideoHeight) {
                        height = width * mVideoHeight / mVideoWidth;
                    }
                }
                break;
            default:
                Log.w(TAG, "wrong screen mode : " + screenMode);
                break;
        }
        if (LOG) {
            Log.v(TAG, "onMeasure() set size: " + width + 'x' + height);
            Log.v(TAG, "onMeasure() video size: " + mVideoWidth + 'x' + mVideoHeight);
            Log.v(TAG, "onMeasure() mNeedWaitLayout=" + mNeedWaitLayout);
        }
        setMeasuredDimension(width, height);
        if (mNeedWaitLayout) { // when OnMeasure ok, start video.
            mNeedWaitLayout = false;
            mHandler.sendEmptyMessage(MSG_LAYOUT_READY);
        }
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(CodeauroraVideoView.class.getName());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(CodeauroraVideoView.class.getName());
    }

    public int resolveAdjustedSize(int desiredSize, int measureSpec) {
        return getDefaultSize(desiredSize, measureSpec);
    }

    private void initVideoView() {
        mVideoWidth = 0;
        mVideoHeight = 0;
        getHolder().addCallback(mSHCallback);
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        mCurrentState = STATE_IDLE;
        mTargetState  = STATE_IDLE;
    }

    private void initialize() {
        mPreparedListener = new MediaPlayer.OnPreparedListener() {
            public void onPrepared(final MediaPlayer mp) {
                if (LOG) {
                    Log.v(TAG, "mPreparedListener.onPrepared(" + mp + ")");
                }
                //Here we can get meta data from mediaplayer.
                // Get the capabilities of the player for this stream
                final Metadata data = mp.getMetadata(MediaPlayer.METADATA_ALL,
                                          MediaPlayer.BYPASS_METADATA_FILTER);
                if (data != null) {
                    mCanPause = !data.has(Metadata.PAUSE_AVAILABLE)
                            || data.getBoolean(Metadata.PAUSE_AVAILABLE);
                    mCanSeekBack = !data.has(Metadata.SEEK_BACKWARD_AVAILABLE)
                            || data.getBoolean(Metadata.SEEK_BACKWARD_AVAILABLE);
                    mCanSeekForward = !data.has(Metadata.SEEK_FORWARD_AVAILABLE)
                            || data.getBoolean(Metadata.SEEK_FORWARD_AVAILABLE);
                    mCanSeek = !data.has(Metadata.SEEK_AVAILABLE)
                            || data.getBoolean(Metadata.SEEK_AVAILABLE);
                } else {
                    mCanPause = true;
                    mCanSeekBack = true;
                    mCanSeekForward = true;
                    mCanSeek = true;
                    Log.w(TAG, "Metadata is null!");
                }
                if (LOG) {
                    Log.v(TAG, "mPreparedListener.onPrepared() mCanPause=" + mCanPause);
                }
                mHasGotPreparedCallBack = true;
                doPreparedIfReady(mMediaPlayer);
            }
        };

        mErrorListener = new MediaPlayer.OnErrorListener() {
            public boolean onError(final MediaPlayer mp, final int frameworkErr, final int implErr) {
                Log.d(TAG, "Error: " + frameworkErr + "," + implErr);
                //record error position and duration
                //here disturb the original logic
                mSeekWhenPrepared = getCurrentPosition();
                if (LOG) {
                    Log.v(TAG, "onError() mSeekWhenPrepared=" + mSeekWhenPrepared + ", mDuration=" + mDuration);
                }
                //for old version Streaming server, getduration is not valid.
                mDuration = Math.abs(mDuration);
                mCurrentState = STATE_ERROR;
                mTargetState = STATE_ERROR;
                if (mMediaController != null) {
                    mMediaController.hide();
                }

                /* If an error handler has been supplied, use it and finish. */
                if (mOnErrorListener != null) {
                    if (mOnErrorListener.onError(mMediaPlayer, frameworkErr, implErr)) {
                        return true;
                    }
                }

                /* Otherwise, pop up an error dialog so the user knows that
                 * something bad has happened. Only try and pop up the dialog
                 * if we're attached to a window. When we're going away and no
                 * longer have a window, don't bother showing the user an error.
                 */
                if (getWindowToken() != null) {
                    final Resources r = mContext.getResources();
                    int messageId;

                    if (frameworkErr == MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
                        messageId = com.android.internal.R.string.VideoView_error_text_invalid_progressive_playback;
                    } else {
                        messageId = com.android.internal.R.string.VideoView_error_text_unknown;
                    }
                     new AlertDialog.Builder(mContext)
                        .setMessage(messageId)
                        .setPositiveButton(com.android.internal.R.string.VideoView_error_button,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        /* If we get here, there is no onError listener, so
                                         * at least inform them that the video is over.
                                         */
                                        if (mOnCompletionListener != null) {
                                            mOnCompletionListener.onCompletion(mMediaPlayer);
                                        }
                                    }
                                })
                        .setCancelable(false)
                        .show();
                }
                return true;
            }
        };

        mBufferingUpdateListener = new MediaPlayer.OnBufferingUpdateListener() {
            public void onBufferingUpdate(final MediaPlayer mp, final int percent) {
                mCurrentBufferPercentage = percent;
                if (mOnBufferingUpdateListener != null) {
                    mOnBufferingUpdateListener.onBufferingUpdate(mp, percent);
                }
                if (LOG) {
                    Log.v(TAG, "onBufferingUpdate() Buffering percent: " + percent);
                    Log.v(TAG, "onBufferingUpdate() mTargetState=" + mTargetState);
                    Log.v(TAG, "onBufferingUpdate() mCurrentState=" + mCurrentState);
                }
            }
        };

        mSizeChangedListener = new MediaPlayer.OnVideoSizeChangedListener() {
            public void onVideoSizeChanged(final MediaPlayer mp, final int width, final int height) {
                mVideoWidth = mp.getVideoWidth();
                mVideoHeight = mp.getVideoHeight();
                if (LOG) {
                    Log.v(TAG, "OnVideoSizeChagned(" + width + "," + height + ")");
                    Log.v(TAG, "OnVideoSizeChagned(" + mVideoWidth + "," + mVideoHeight + ")");
                    Log.v(TAG, "OnVideoSizeChagned() mCurrentState=" + mCurrentState);
                }
                if (mVideoWidth != 0 && mVideoHeight != 0) {
                    getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                    if (mCurrentState == STATE_PREPARING) {
                        mNeedWaitLayout = true;
                    }
                }
                if (mVideoSizeListener != null) {
                    mVideoSizeListener.onVideoSizeChanged(mp, width, height);
                }
                CodeauroraVideoView.this.requestLayout();
            }
        };

        getHolder().removeCallback(mSHCallback);
        mSHCallback = new SurfaceHolder.Callback() {
            public void surfaceChanged(final SurfaceHolder holder, final int format, 
                    final int w, final int h) {
                if (LOG) {
                    Log.v(TAG, "surfaceChanged(" + holder + ", " + format
                            + ", " + w + ", " + h + ")");
                    Log.v(TAG, "surfaceChanged() mMediaPlayer=" + mMediaPlayer
                            + ", mTargetState=" + mTargetState
                            + ", mVideoWidth=" + mVideoWidth 
                            + ", mVideoHeight=" + mVideoHeight);
                }
                mSurfaceWidth = w;
                mSurfaceHeight = h;
                final boolean isValidState =  (mTargetState == STATE_PLAYING);
                final boolean hasValidSize = (mVideoWidth == w && mVideoHeight == h);
                if (mMediaPlayer != null && isValidState && hasValidSize) {
                    if (mSeekWhenPrepared != 0) {
                        seekTo(mSeekWhenPrepared);
                    }
                    Log.v(TAG, "surfaceChanged() start()");
                    start();
                }
            }

            public void surfaceCreated(final SurfaceHolder holder) {
                if (LOG) {
                    Log.v(TAG, "surfaceCreated(" + holder + ")");
                }
                /*
                if (mCurrentState == STATE_SUSPENDED) {
                    mSurfaceHolder = holder;
                    mMediaPlayer.setDisplay(mSurfaceHolder);
                    if (mMediaPlayer.resume()) {
                        mCurrentState = STATE_PREPARED;
                        if (mSeekWhenPrepared != 0) {
                            seekTo(mSeekWhenPrepared);
                        }
                        if (mTargetState == STATE_PLAYING) {
                            start();
                        }
                        return;
                    } else {
                        release(false);
                    }
                }
                */
                if (mCurrentState == STATE_SUSPENDED) {
                    mSurfaceHolder = holder;
                    mMediaPlayer.setDisplay(mSurfaceHolder);
                    release(false);
                }
                mSurfaceHolder = holder;
                openVideo();
            }

            public void surfaceDestroyed(final SurfaceHolder holder) {
                // after we return from this we can't use the surface any more
                if (LOG) {
                    Log.v(TAG, "surfaceDestroyed(" + holder + ")");
                }
                mSurfaceHolder = null;
                if (mMediaController != null) {
                    mMediaController.hide();
                }
                if (isHTTPStreaming(mUri) && mCurrentState == STATE_SUSPENDED) {
                    // Don't call release() while run suspend operation
                    return;
                }
                release(true);
            }
        };
        getHolder().addCallback(mSHCallback);
    }

    public void setVideoPath(String path) {
        setVideoURI(Uri.parse(path));
    }

    public void setVideoURI(Uri uri) {
        setVideoURI(uri, null);
    }

    /**
     * @hide
     */
    public void setVideoURI(Uri uri, Map<String, String> headers) {
        Log.d(TAG,"setVideoURI uri = " + uri);
        mDuration = -1;
        setResumed(true);
        mUri = uri;
        mHeaders = headers;
        mSeekWhenPrepared = 0;
        openVideo();
        requestLayout();
        invalidate();
    }

    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            mTargetState  = STATE_IDLE;
        }
    }

    private void openVideo() {
        clearVideoInfo();
        if (mUri == null || mSurfaceHolder == null) {
            // not ready for playback just yet, will try again later
            return;
        }

        // we shouldn't clear the target state, because somebody might have
        // called start() previously
        release(false);
        if ("".equalsIgnoreCase(String.valueOf(mUri))) {
            Log.w(TAG, "Unable to open content: " + mUri);
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        }
        try {
            mMediaPlayer = new MediaPlayer();
            if (mAudioSession != 0) {
                mMediaPlayer.setAudioSessionId(mAudioSession);
            } else {
                mAudioSession = mMediaPlayer.getAudioSessionId();
            }
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnInfoListener(mOnInfoListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mCurrentBufferPercentage = 0;
            mMediaPlayer.setDataSource(mContext, mUri, mHeaders);
            mMediaPlayer.setDisplay(mSurfaceHolder);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.prepareAsync();
            // we don't set the target state here either, but preserve the
            // target state that was there before.
            mCurrentState = STATE_PREPARING;
            attachMediaController();
        } catch (IOException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        }
    }

    public void setMediaController(MediaController controller) {
        if (mMediaController != null) {
            mMediaController.hide();
        }
        mMediaController = controller;
        attachMediaController();
    }

    private void attachMediaController() {
        if (mMediaPlayer != null && mMediaController != null) {
            mMediaController.setMediaPlayer(this);
            View anchorView = this.getParent() instanceof View ?
                    (View)this.getParent() : this;
            mMediaController.setAnchorView(anchorView);
            mMediaController.setEnabled(isInPlaybackState());
        }
    }

    private boolean isHTTPStreaming(Uri mUri) {
        if (mUri != null){
            String scheme = mUri.toString();
            if (scheme.startsWith("http://") || scheme.startsWith("https://")) {
                if (scheme.endsWith(".m3u8") || scheme.endsWith(".m3u")
                    || scheme.contains("m3u8") || scheme.endsWith(".mpd")) {
                    // HLS or DASH streaming source
                    return false;
                }
                // HTTP streaming
                return true;
            }
        }
        return false;
    }

    MediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
        new MediaPlayer.OnVideoSizeChangedListener() {
            public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                mVideoWidth = mp.getVideoWidth();
                mVideoHeight = mp.getVideoHeight();
                if (mVideoWidth != 0 && mVideoHeight != 0) {
                    getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                    requestLayout();
                }
            }
    };

    private MediaPlayer.OnCompletionListener mCompletionListener =
        new MediaPlayer.OnCompletionListener() {
        public void onCompletion(MediaPlayer mp) {
            mCurrentState = STATE_PLAYBACK_COMPLETED;
            mTargetState = STATE_PLAYBACK_COMPLETED;
            if (mMediaController != null) {
                mMediaController.hide();
            }
            if (mOnCompletionListener != null) {
                mOnCompletionListener.onCompletion(mMediaPlayer);
            }
        }
    };

    private MediaPlayer.OnErrorListener mErrorListener;

    private MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
        new MediaPlayer.OnBufferingUpdateListener() {
        public void onBufferingUpdate(MediaPlayer mp, int percent) {
            mCurrentBufferPercentage = percent;
        }
    };

    /**
     * Register a callback to be invoked when the media file
     * is loaded and ready to go.
     *
     * @param l The callback that will be run
     */
    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l) {
        mOnPreparedListener = l;
    }

    /**
     * Register a callback to be invoked when the end of a media file
     * has been reached during playback.
     *
     * @param l The callback that will be run
     */
    public void setOnCompletionListener(OnCompletionListener l) {
        mOnCompletionListener = l;
    }

    /**
     * Register a callback to be invoked when an error occurs
     * during playback or setup.  If no listener is specified,
     * or if the listener returned false, VideoView will inform
     * the user of any errors.
     *
     * @param l The callback that will be run
     */
    public void setOnErrorListener(OnErrorListener l) {
        mOnErrorListener = l;
    }

    /**
     * Register a callback to be invoked when an informational event
     * occurs during playback or setup.
     *
     * @param l The callback that will be run
     */
    public void setOnInfoListener(OnInfoListener l) {
        mOnInfoListener = l;
    }

    SurfaceHolder.Callback mSHCallback = new SurfaceHolder.Callback() {
        public void surfaceChanged(SurfaceHolder holder, int format,
                                    int w, int h) {
            mSurfaceWidth = w;
            mSurfaceHeight = h;
            boolean isValidState =  (mTargetState == STATE_PLAYING);
            boolean hasValidSize = (mVideoWidth == w && mVideoHeight == h);
            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if (mSeekWhenPrepared != 0) {
                    seekTo(mSeekWhenPrepared);
                }
                start();
            }
        }

        public void surfaceCreated(SurfaceHolder holder) {
            /*
            if (mCurrentState == STATE_SUSPENDED) {
                mSurfaceHolder = holder;
                mMediaPlayer.setDisplay(mSurfaceHolder);
                if (mMediaPlayer.resume()) {
                    mCurrentState = STATE_PREPARED;
                    if (mSeekWhenPrepared != 0) {
                        seekTo(mSeekWhenPrepared);
                    }
                    if (mTargetState == STATE_PLAYING) {
                        start();
                    }
                    return;
                } else {
                    release(false);
                }
            }
            mSurfaceHolder = holder;
            openVideo();
            */
            if (LOG) {
                Log.v(TAG, "surfaceCreated(" + holder + ")");
            }
            if (mCurrentState == STATE_SUSPENDED) {
                mSurfaceHolder = holder;
                mMediaPlayer.setDisplay(mSurfaceHolder);
                release(false);
            }
            mSurfaceHolder = holder;
            openVideo();
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // after we return from this we can't use the surface any more
            mSurfaceHolder = null;
            if (mMediaController != null) mMediaController.hide();
            if (isHTTPStreaming(mUri) && mCurrentState == STATE_SUSPENDED) {
                // Don't call release() while run suspend operation
                return;
            }
            release(true);
        }
    };

    /*
     * release the media player in any state
     */
    private void release(boolean cleartargetstate) {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            if (cleartargetstate) {
                mTargetState  = STATE_IDLE;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null) {
            toggleMediaControlsVisiblity();
        }
        return false;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null) {
            toggleMediaControlsVisiblity();
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final boolean isKeyCodeSupported = keyCode != KeyEvent.KEYCODE_BACK &&
                keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
                keyCode != KeyEvent.KEYCODE_VOLUME_MUTE &&
                keyCode != KeyEvent.KEYCODE_MENU &&
                keyCode != KeyEvent.KEYCODE_CALL &&
                keyCode != KeyEvent.KEYCODE_ENDCALL &&
                keyCode != KeyEvent.KEYCODE_CAMERA;
        if (isInPlaybackState() && isKeyCodeSupported && mMediaController != null) {
            if (event.getRepeatCount() == 0 && (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                } else {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (!mMediaPlayer.isPlaying()) {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_REWIND ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                    keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
                // consume media action, so if video view if front,
                // other media player will not play any sounds.
                return true;
            } else {
                toggleMediaControlsVisiblity();
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void toggleMediaControlsVisiblity() {
        if (mMediaController.isShowing()) {
            mMediaController.hide();
        } else {
            mMediaController.show();
        }
    }

    @Override
    public void start() {
        if (isInPlaybackState()) {
            mMediaPlayer.start();
            mCurrentState = STATE_PLAYING;
        }
        mTargetState = STATE_PLAYING;
    }

    @Override
    public void pause() {
        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                mCurrentState = STATE_PAUSED;
            }
        }
        mTargetState = STATE_PAUSED;
    }

    public void suspend() {
        /*
        // HTTP streaming will call mMediaPlayer->suspend(), others will call release()
        if (isHTTPStreaming(mUri) && mCurrentState != STATE_PREPARING) {
            if (mMediaPlayer != null) {
                if (mMediaPlayer.suspend()) {
                    mTargetState = mCurrentState;
                    mCurrentState = STATE_SUSPENDED;
                    return;
                }
            }
        }*/
        release(false);
    }

    public void resume() {
        /*
        // HTTP streaming (with suspended status) will call mMediaPlayer->resume(), others will call openVideo()
        if (mCurrentState == STATE_SUSPENDED) {
            if (mSurfaceHolder != null) {
                // The surface hasn't been destroyed
                if (mMediaPlayer.resume()) {
                    mCurrentState = STATE_PREPARED;
                    if (mSeekWhenPrepared !=0) {
                        seekTo(mSeekWhenPrepared);
                    }
                    if (mTargetState == STATE_PLAYING) {
                        start();
                    }
                    return;
                } else {
                     // resume failed, so call release() before openVideo()
                     release(false);
                }
            } else {
                // The surface has been destroyed, resume operation will be done after surface created
                return;
            }
        }*/
        // HTTP streaming (with suspended status) will call mMediaPlayer->resume(), others will call openVideo()
        if (mCurrentState == STATE_SUSPENDED) {
            if (mSurfaceHolder != null) {
                 release(false);
            } else {
                // The surface has been destroyed, resume operation will be done after surface created
                return;
            }
        }
        openVideo();
    }

    @Override
    public int getDuration() {
        final boolean inPlaybackState = isInPlaybackState();
        if (LOG) {
            Log.v(TAG, "getDuration() mDuration=" + mDuration + ", inPlaybackState="
                    + inPlaybackState);
        }
        if (inPlaybackState) {
            if (mDuration > 0) {
                return mDuration;
            }
            // in case the duration is zero or smaller than zero for streaming
            // video
            int tempDuration = mMediaPlayer.getDuration();
            if (tempDuration <= 0) {
                return mDuration;
            } else {
                mDuration = tempDuration;
            }

            return mDuration;
        }
        return mDuration;
    }

    @Override
    public int getCurrentPosition() {
        int position = 0;
        if (mSeekWhenPrepared > 0) {
            // if connecting error before seek,
            // we should remember this position for retry
            position = mSeekWhenPrepared;
            // /M: if player not started, getCurrentPosition() will lead to NE.
        } else if (isInPlaybackState()) {
            position = mMediaPlayer.getCurrentPosition();
        }
        if (LOG) {
            Log.v(TAG, "getCurrentPosition() return " + position
                    + ", mSeekWhenPrepared=" + mSeekWhenPrepared);
        }
        return position;
    }

    @Override
    public void seekTo(int msec) {
        if (isInPlaybackState()) {
            mMediaPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    @Override
    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }

    private boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
                mCurrentState != STATE_ERROR &&
                mCurrentState != STATE_IDLE &&
                mCurrentState != STATE_PREPARING &&
                mCurrentState != STATE_SUSPENDED);
    }

    @Override
    public boolean canPause() {
        return mCanPause;
    }

    @Override
    public boolean canSeekBackward() {
        return mCanSeekBack;
    }

    @Override
    public boolean canSeekForward() {
        return mCanSeekForward;
    }

    public boolean canSeek() {
        return mCanSeek;
    }

    @Override
    public int getAudioSessionId() {
        if (mAudioSession == 0) {
            MediaPlayer foo = new MediaPlayer();
            mAudioSession = foo.getAudioSessionId();
            foo.release();
        }
        return mAudioSession;
    }

    // for duration displayed
    public void setDuration(final int duration) {
        if (LOG) {
            Log.v(TAG, "setDuration(" + duration + ")");
        }
        mDuration = (duration > 0 ? -duration : duration);
    }

    public void setVideoURI(final Uri uri, final Map<String, String> headers,
            final boolean hasGotMetaData) {
        if (LOG) {
            Log.v(TAG, "setVideoURI(" + uri + ", " + headers + ")");
        }
        // clear the flags
        mHasGotMetaData = hasGotMetaData;
        setVideoURI(uri, headers);
    }

    private void doPreparedIfReady(final MediaPlayer mp) {
        if (LOG) {
            Log.v(TAG, "doPreparedIfReady() mHasGotPreparedCallBack=" + mHasGotPreparedCallBack
                    + ", mHasGotMetaData=" + mHasGotMetaData + ", mNeedWaitLayout="
                    + mNeedWaitLayout
                    + ", mCurrentState=" + mCurrentState);
        }
        if (mHasGotPreparedCallBack && mHasGotMetaData && !mNeedWaitLayout) {
            doPrepared(mp);
        }
    }

    private void doPrepared(final MediaPlayer mp) {
        if (LOG) {
            Log.v(TAG, "doPrepared(" + mp + ") start");
        }
        mCurrentState = STATE_PREPARED;
        if (mOnPreparedListener != null) {
            mOnPreparedListener.onPrepared(mMediaPlayer);
        }
        mVideoWidth = mp.getVideoWidth();
        mVideoHeight = mp.getVideoHeight();

        // mSeekWhenPrepared may be changed after seekTo()
        final int seekToPosition = mSeekWhenPrepared;
        if (seekToPosition != 0) {
            seekTo(seekToPosition);
        }
        if (mVideoWidth != 0 && mVideoHeight != 0) {
            getHolder().setFixedSize(mVideoWidth, mVideoHeight);
        }

        if (mTargetState == STATE_PLAYING) {
            start();
        }
        if (LOG) {
            Log.v(TAG, "doPrepared() end video size: " + mVideoWidth + "," + mVideoHeight
                    + ", mTargetState=" + mTargetState + ", mCurrentState=" + mCurrentState);
        }
    }

    /**
     * surfaceCreate will invoke openVideo after the activity stoped. Here set
     * this flag to avoid openVideo after the activity stoped.
     *
     * @param resume
     */
    public void setResumed(final boolean resume) {
        if (LOG) {
            Log.v(TAG, "setResumed(" + resume + ") mUri=" + mUri + ", mOnResumed=" + mOnResumed);
        }
        mOnResumed = resume;
    }

    private void clearVideoInfo() {
        if (LOG) {
            Log.v(TAG, "clearVideoInfo()");
        }
        mHasGotPreparedCallBack = false;
        mNeedWaitLayout = false;
    }

    public void clearSeek() {
        if (LOG) {
            Log.v(TAG, "clearSeek() mSeekWhenPrepared=" + mSeekWhenPrepared);
        }
        mSeekWhenPrepared = 0;
    }

    public void clearDuration() {
        if (LOG) {
            Log.v(TAG, "clearDuration() mDuration=" + mDuration);
        }
        mDuration = -1;
    }

    public boolean isTargetPlaying() {
        if (LOG) {
            Log.v(TAG, "isTargetPlaying() mTargetState=" + mTargetState);
        }
        return mTargetState == STATE_PLAYING;
    }

    public void setScreenModeManager(final ScreenModeManager manager) {
        mScreenManager = manager;
        if (mScreenManager != null) {
            mScreenManager.addListener(this);
        }
        if (LOG) {
            Log.v(TAG, "setScreenModeManager(" + manager + ")");
        }
    }

    @Override
    public void onScreenModeChanged(final int newMode) {
        this.requestLayout();
    }

    public void setOnVideoSizeChangedListener(final OnVideoSizeChangedListener l) {
        mVideoSizeListener = l;
        if (LOG) {
            Log.i(TAG, "setOnVideoSizeChangedListener(" + l + ")");
        }
    }

    public void setOnBufferingUpdateListener(final OnBufferingUpdateListener l) {
        mOnBufferingUpdateListener = l;
        if (LOG) {
            Log.v(TAG, "setOnBufferingUpdateListener(" + l + ")");
        }
    }
}
