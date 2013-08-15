package com.qcom.gallery3d.video;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.sqlite.SQLiteException;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.media.Metadata;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.widget.VideoView;

import com.android.gallery3d.ui.Log;
import com.qcom.gallery3d.ext.QcomLog;
import com.qcom.gallery3d.video.ScreenModeManager.ScreenModeListener;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;

import java.io.IOException;
import java.util.Map;

/**
 * QcomVideoView enhance the streaming videoplayer process and UI.
 * It only supports QcomMediaController. 
 * If you set android's default MediaController,
 * some state will not be shown well.
 * Moved from the package android.widget
 */
public class QcomVideoView extends VideoView implements ScreenModeListener {
    private static final String TAG = "QcomVideoView";
    private static final boolean LOG = true;
    
    //add info listener to get info whether can get meta data or not for rtsp.
    private MediaPlayer.OnInfoListener mOnInfoListener;
    private MediaPlayer.OnBufferingUpdateListener mOnBufferingUpdateListener;
    private MediaPlayer.OnVideoSizeChangedListener mVideoSizeListener;
    
    //when the streaming type is live, metadata maybe not right when prepared.
    private boolean mHasGotMetaData = false;
    private boolean mHasGotPreparedCallBack = false;
    
    private final MediaPlayer.OnInfoListener mInfoListener = new MediaPlayer.OnInfoListener() {
        
        public boolean onInfo(final MediaPlayer mp, final int what, final int extra) {
            if (LOG) {
                QcomLog.v(TAG, "onInfo() what:" + what + " extra:" + extra);
            }
            if (mOnInfoListener != null && mOnInfoListener.onInfo(mp, what, extra)) {
                return true;
            } else {
            	// TODO comments by sunlei
//                if (what == MediaPlayer.MEDIA_INFO_METADATA_CHECK_COMPLETE) {
//                    mHasGotMetaData = true;
//                    doPreparedIfReady(mMediaPlayer);
//                    return true;
//                }
            }
            return false;
        }
        
    };
    
    private void doPreparedIfReady(final MediaPlayer mp) {
        if (LOG) {
            QcomLog.v(TAG, "doPreparedIfReady() mHasGotPreparedCallBack=" + mHasGotPreparedCallBack
                    + ", mHasGotMetaData=" + mHasGotMetaData + ", mNeedWaitLayout=" + mNeedWaitLayout
                    + ", mCurrentState=" + mCurrentState);
        }
        if (mHasGotPreparedCallBack && mHasGotMetaData && !mNeedWaitLayout) {
            doPrepared(mp);
        }
    }

    public QcomVideoView(final Context context) {
        super(context);
        initialize();
    }

    public QcomVideoView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public QcomVideoView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }
    
    private void initialize() {
        mPreparedListener = new MediaPlayer.OnPreparedListener() {
            public void onPrepared(final MediaPlayer mp) {
                if (LOG) {
                    QcomLog.v(TAG, "mPreparedListener.onPrepared(" + mp + ")");
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
                } else {
                    mCanPause = true;
                    mCanSeekBack = true;
                    mCanSeekForward = true;
                    QcomLog.w(TAG, "Metadata is null!");
                }
                if (LOG) {
                    QcomLog.v(TAG, "mPreparedListener.onPrepared() mCanPause=" + mCanPause);
                }
                mHasGotPreparedCallBack = true;
                doPreparedIfReady(mMediaPlayer);
            }
        };
        
        mErrorListener = new MediaPlayer.OnErrorListener() {
            public boolean onError(final MediaPlayer mp, final int frameworkErr, final int implErr) {
                Log.d(TAG, "Error: " + frameworkErr + "," + implErr);
                if (mCurrentState == STATE_ERROR) {
                    Log.w(TAG, "Duplicate error message. error message has been sent! " +
                            "error=(" + frameworkErr + "," + implErr + ")");
                    return true;
                }
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
                    
                    // TODO comments by sunlei
//                    if (frameworkErr == MediaPlayer.MEDIA_ERROR_BAD_FILE) {
//                        messageId = com.mediatek.R.string.VideoView_error_text_bad_file;
//                    } else if (frameworkErr == MediaPlayer.MEDIA_ERROR_CANNOT_CONNECT_TO_SERVER) {
//                        messageId = com.mediatek.R.string.VideoView_error_text_cannot_connect_to_server;
//                    } else if (frameworkErr == MediaPlayer.MEDIA_ERROR_TYPE_NOT_SUPPORTED) {
//                        messageId = com.mediatek.R.string.VideoView_error_text_type_not_supported;
//                    } else if (frameworkErr == MediaPlayer.MEDIA_ERROR_DRM_NOT_SUPPORTED) {
//                        messageId = com.mediatek.R.string.VideoView_error_text_drm_not_supported;
//                    } else if (frameworkErr == MediaPlayer.MEDIA_ERROR_INVALID_CONNECTION) {
//                        messageId = com.mediatek.internal.R.string.VideoView_error_text_invalid_connection;
//                    } else if (frameworkErr == MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
//                        messageId = com.android.internal.R.string.VideoView_error_text_invalid_progressive_playback;
//                    } else {
//                        messageId = com.android.internal.R.string.VideoView_error_text_unknown;
//                    }
                    
                    if (frameworkErr == MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
                        messageId = com.android.internal.R.string.VideoView_error_text_invalid_progressive_playback;
                    } else {
                        messageId = com.android.internal.R.string.VideoView_error_text_unknown;
                    }
                    
                    final String errorDialogTag = "ERROR_DIALOG_TAG";
                    FragmentManager fragmentManager = ((Activity)mContext).getFragmentManager();
                    DialogFragment oldFragment = (DialogFragment) fragmentManager
                            .findFragmentByTag(errorDialogTag);
                    if (null != oldFragment) {
                        oldFragment.dismissAllowingStateLoss();
                    }
                    DialogFragment newFragment = ErrorDialogFragment.newInstance(messageId);
                    newFragment.show(fragmentManager, errorDialogTag);
                    fragmentManager.executePendingTransactions();
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
                    QcomLog.v(TAG, "onBufferingUpdate() Buffering percent: " + percent);
                }
                if (LOG) {
                    QcomLog.v(TAG, "onBufferingUpdate() mTargetState=" + mTargetState);
                }
                if (LOG) {
                    QcomLog.v(TAG, "onBufferingUpdate() mCurrentState=" + mCurrentState);
                }
            }
        };
        
        mSizeChangedListener = new MediaPlayer.OnVideoSizeChangedListener() {
            public void onVideoSizeChanged(final MediaPlayer mp, final int width, final int height) {
                mVideoWidth = mp.getVideoWidth();
                mVideoHeight = mp.getVideoHeight();
                if (LOG) {
                    QcomLog.v(TAG, "OnVideoSizeChagned(" + width + "," + height + ")");
                }
                if (LOG) {
                    QcomLog.v(TAG, "OnVideoSizeChagned(" + mVideoWidth + "," + mVideoHeight + ")");
                }
                if (LOG) {
                    QcomLog.v(TAG, "OnVideoSizeChagned() mCurrentState=" + mCurrentState);
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
                QcomVideoView.this.requestLayout();
            }
        };
        
        getHolder().removeCallback(mSHCallback);
        mSHCallback = new SurfaceHolder.Callback() {
            public void surfaceChanged(final SurfaceHolder holder, final int format, final int w, final int h) {
                if (LOG) {
                    Log.v(TAG, "surfaceChanged(" + holder + ", " + format + ", " + w + ", " + h + ")");
                }
                if (LOG) {
                    Log.v(TAG, "surfaceChanged() mMediaPlayer=" + mMediaPlayer + ", mTargetState=" + mTargetState
                            + ", mVideoWidth=" + mVideoWidth + ", mVideoHeight=" + mVideoHeight);
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
                release(true);
            }
        };
        getHolder().addCallback(mSHCallback);
    }
    
    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        //Log.i("@@@@", "onMeasure");
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
                if (mVideoWidth * height  > width * mVideoHeight) {
                    //Log.i("@@@", "image too tall, correcting");
                    height = width * mVideoHeight / mVideoWidth;
                } else if (mVideoWidth * height  < width * mVideoHeight) {
                    //Log.i("@@@", "image too wide, correcting");
                    width = height * mVideoWidth / mVideoHeight;
                } /*else {
                    //Log.i("@@@", "aspect ratio is correct: " +
                            //width+"/"+height+"="+
                            //mVideoWidth+"/"+mVideoHeight);
                }*/
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
                if (mVideoWidth * height  > width * mVideoHeight) {
                    //extend width to be cropped
                    width = height * mVideoWidth / mVideoHeight;
                } else if (mVideoWidth * height  < width * mVideoHeight) {
                    //extend height to be cropped
                    height = width * mVideoHeight / mVideoWidth;
                }
            }
            break;
        default:
            QcomLog.w(TAG, "wrong screen mode : " + screenMode);
            break;
        }
        if (LOG) {
            QcomLog.v(TAG, "onMeasure() set size: " + width + 'x' + height);
            QcomLog.v(TAG, "onMeasure() video size: " + mVideoWidth + 'x' + mVideoHeight);
            QcomLog.v(TAG, "onMeasure() mNeedWaitLayout=" + mNeedWaitLayout);
        }
        setMeasuredDimension(width, height);
        if (mNeedWaitLayout) { //when OnMeasure ok, start video.
            mNeedWaitLayout = false;
            mHandler.sendEmptyMessage(MSG_LAYOUT_READY);
        }
    }
    
//    @Override
//    public boolean onTouchEvent(MotionEvent ev) {
//        if (LOG) Log.v(TAG, "onTouchEvent(" + ev + ")");
//        if (mMediaController != null) {
//            toggleMediaControlsVisiblity();
//        }
//        return false;
//    }
    
    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
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
            } else if (keyCode ==  KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || 
                    keyCode ==  KeyEvent.KEYCODE_MEDIA_NEXT ||
                    keyCode ==  KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                    keyCode ==  KeyEvent.KEYCODE_MEDIA_REWIND ||
                    keyCode ==  KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                    keyCode ==  KeyEvent.KEYCODE_HEADSETHOOK) {
                //consume media action, so if video view if front,
                //other media player will not play any sounds.
                return true;
            } else {
                toggleMediaControlsVisiblity();
            }
        }

        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public void setVideoURI(final Uri uri, final Map<String, String> headers) {
        mDuration = -1;
        setResumed(true);
        super.setVideoURI(uri, headers);
    }
    
    public void setVideoURI(final Uri uri, final Map<String, String> headers, final boolean hasGotMetaData) {
        if (LOG) {
            QcomLog.v(TAG, "setVideoURI(" + uri + ", " + headers + ")");
        }
        //clear the flags
        mHasGotMetaData = hasGotMetaData;
        setVideoURI(uri, headers);
    }
    
    private void clearVideoInfo() {
        if (LOG) {
            Log.v(TAG, "clearVideoInfo()");
        }
        mHasGotPreparedCallBack = false;
        mNeedWaitLayout = false;
    }
    
    @Override
    protected void openVideo() {
        if (LOG) {
            Log.v(TAG, "openVideo() mUri=" + mUri + ", mSurfaceHolder=" + mSurfaceHolder
                    + ", mSeekWhenPrepared=" + mSeekWhenPrepared + ", mMediaPlayer=" + mMediaPlayer
                    + ", mOnResumed=" + mOnResumed);
        }
        clearVideoInfo();
        if (!mOnResumed || mUri == null || mSurfaceHolder == null) {
            // not ready for playback just yet, will try again later
            return;
        }
        
        // Tell the music playback service to pause
        // TODO: these constants need to be published somewhere in the framework.
        final Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");
        mContext.sendBroadcast(i);

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
            //end update status.
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            //mDuration = -1;
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            mCurrentBufferPercentage = 0;
            Log.w(TAG, "openVideo setDataSource()");
            mMediaPlayer.setDataSource(mContext, mUri, mHeaders);
            mMediaPlayer.setDisplay(mSurfaceHolder);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            Log.w(TAG, "openVideo prepareAsync()");
            mMediaPlayer.prepareAsync();
            // we don't set the target state here either, but preserve the
            // target state that was there before.
            mCurrentState = STATE_PREPARING;
            //attachMediaController();
        } catch (final IOException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        } catch (final IllegalArgumentException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        } catch (final SQLiteException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        }
        if (LOG) {
            Log.v(TAG, "openVideo() mUri=" + mUri + ", mSurfaceHolder=" + mSurfaceHolder
                    + ", mSeekWhenPrepared=" + mSeekWhenPrepared + ", mMediaPlayer=" + mMediaPlayer);
        }
    }

    private void doPrepared(final MediaPlayer mp) {
        if (LOG) {
            QcomLog.v(TAG, "doPrepared(" + mp + ") start");
        }
        mCurrentState = STATE_PREPARED;
        if (mOnPreparedListener != null) {
            mOnPreparedListener.onPrepared(mMediaPlayer);
        }
        mVideoWidth = mp.getVideoWidth();
        mVideoHeight = mp.getVideoHeight();

        final int seekToPosition = mSeekWhenPrepared;  // mSeekWhenPrepared may be changed after seekTo() call
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
            QcomLog.v(TAG, "doPrepared() end video size: " + mVideoWidth + "," + mVideoHeight
                    + ", mTargetState=" + mTargetState + ", mCurrentState=" + mCurrentState);
        }
    }
    
    private boolean mOnResumed;
    /**
     * surfaceCreate will invoke openVideo after the activity stoped.
     * Here set this flag to avoid openVideo after the activity stoped.
     * @param resume
     */
    public void setResumed(final boolean resume) {
        if (LOG) {
            QcomLog.v(TAG, "setResumed(" + resume + ") mUri=" + mUri + ", mOnResumed=" + mOnResumed);
        }
        mOnResumed = resume;
    }
    
    @Override
    public void resume() {
        if (LOG) {
            QcomLog.v(TAG, "resume() mTargetState=" + mTargetState + ", mCurrentState=" + mCurrentState);
        }
        setResumed(true);
        openVideo();
    }
    
    @Override
    public void suspend() {
        if (LOG) {
            QcomLog.v(TAG, "suspend() mTargetState=" + mTargetState + ", mCurrentState=" + mCurrentState);
        }
        super.suspend();
    }
    
    public void setOnInfoListener(final OnInfoListener l) {
        mOnInfoListener = l;
        if (LOG) {
            QcomLog.v(TAG, "setInfoListener(" + l + ")");
        }
    }
    
    public void setOnBufferingUpdateListener(final OnBufferingUpdateListener l) {
        mOnBufferingUpdateListener = l;
        if (LOG) {
            QcomLog.v(TAG, "setOnBufferingUpdateListener(" + l + ")");
        }
    }
    
    public void setOnVideoSizeChangedListener(final OnVideoSizeChangedListener l) {
        mVideoSizeListener = l;
        if (LOG) {
            Log.i(TAG, "setOnVideoSizeChangedListener(" + l + ")");
        }
    }
    
    @Override
    public int getCurrentPosition() {
        int position = 0;
        if (mSeekWhenPrepared > 0) {
            //if connecting error before seek,
            //we should remember this position for retry
            position = mSeekWhenPrepared;
        ///M: if player not started, getCurrentPosition() will lead to NE.
        } else if (isInPlaybackState() && mCurrentState != STATE_PREPARED) {
            position = mMediaPlayer.getCurrentPosition();
        }
        if (LOG) {
            QcomLog.v(TAG, "getCurrentPosition() return " + position
                    + ", mSeekWhenPrepared=" + mSeekWhenPrepared);
        }
        return position;
    }
    
    //clear the seek position any way.
    //this will effect the case: stop video before it's seek completed.
    public void clearSeek() {
        if (LOG) {
            QcomLog.v(TAG, "clearSeek() mSeekWhenPrepared=" + mSeekWhenPrepared);
        }
        mSeekWhenPrepared = 0;
    }
    
    public boolean isTargetPlaying() {
        if (LOG) {
            Log.v(TAG, "isTargetPlaying() mTargetState=" + mTargetState);
        }
        return mTargetState == STATE_PLAYING;
    }
    
    public void dump() {
        if (LOG) {
            Log.v(TAG, "dump() mUri=" + mUri
                    + ", mTargetState=" + mTargetState + ", mCurrentState=" + mCurrentState
                    + ", mSeekWhenPrepared=" + mSeekWhenPrepared
                    + ", mVideoWidth=" + mVideoWidth + ", mVideoHeight=" + mVideoHeight
                    + ", mMediaPlayer=" + mMediaPlayer + ", mSurfaceHolder=" + mSurfaceHolder);
        }
    }
    
    @Override
    public void seekTo(final int msec) {
        if (LOG) {
            Log.v(TAG, "seekTo(" + msec + ") isInPlaybackState()=" + isInPlaybackState());
        }
        super.seekTo(msec);
    }
    
    @Override
    protected void release(final boolean cleartargetstate) {
        if (LOG) {
            Log.v(TAG, "release(" + cleartargetstate + ") mMediaPlayer=" + mMediaPlayer);
        }
        super.release(cleartargetstate);
    }
    
    //for duration displayed
    public void setDuration(final int duration) {
        if (LOG) {
            Log.v(TAG, "setDuration(" + duration + ")");
        }
        mDuration = (duration > 0 ? -duration : duration);
    }
    
    @Override
    public int getDuration() {
        final boolean inPlaybackState = isInPlaybackState();
        if (LOG) {
            Log.v(TAG, "getDuration() mDuration=" + mDuration + ", inPlaybackState=" + inPlaybackState);
        }
        if (inPlaybackState) {
            if (mDuration > 0) {
                return mDuration;
            }
            mDuration = mMediaPlayer.getDuration();
            return mDuration;
        }
        //mDuration = -1;
        return mDuration;
    }

    public void clearDuration() {
        if (LOG) {
            QcomLog.v(TAG, "clearDuration() mDuration=" + mDuration);
        }
        mDuration = -1;
    }
    
    //for video size changed before started issue
    private static final int MSG_LAYOUT_READY = 1;
    private boolean mNeedWaitLayout = false;
    private final Handler mHandler = new Handler() {
        public void handleMessage(final Message msg) {
            if (LOG) {
                QcomLog.v(TAG, "handleMessage() to do prepare. msg=" + msg);
            }
            switch(msg.what) {
            case MSG_LAYOUT_READY:
                if (mMediaPlayer == null || mUri == null) {
                    QcomLog.w(TAG, "Cannot prepare play! mMediaPlayer=" + mMediaPlayer + ", mUri=" + mUri);
                } else {
                    doPreparedIfReady(mMediaPlayer);
                }
                break;
            default:
                QcomLog.w(TAG, "Unhandled message " + msg);
                break;
            }
        }
    };
    
    private ScreenModeManager mScreenManager;
    public void setScreenModeManager(final ScreenModeManager manager) {
        mScreenManager = manager;
        if (mScreenManager != null) {
            mScreenManager.addListener(this);
        }
        if (LOG) {
            QcomLog.v(TAG, "setScreenModeManager(" + manager + ")");
        }
    }

    @Override
    public void onScreenModeChanged(final int newMode) {
        this.requestLayout();
    }
}
