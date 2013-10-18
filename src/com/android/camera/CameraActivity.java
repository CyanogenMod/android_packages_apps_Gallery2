/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.camera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.camera.ui.CameraSwitcher;
import com.android.gallery3d.R;
import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.util.LightCycleHelper;

public class CameraActivity extends ActivityBase
        implements CameraSwitcher.CameraSwitchListener {
    public static final int PHOTO_MODULE_INDEX = 0;
    public static final int VIDEO_MODULE_INDEX = 1;
    public static final int PANORAMA_MODULE_INDEX = 2;
    public static final int LIGHTCYCLE_MODULE_INDEX = 3;

    CameraModule mCurrentModule;
    private FrameLayout mFrame;
    private ShutterButton mShutter;
    private CameraSwitcher mSwitcher;
    private View mCameraControls;
    private View mControlsBackground;
    private View mPieMenuButton;
    private Drawable[] mDrawables;
    private int mCurrentModuleIndex;
    private MotionEvent mDown;
    private boolean mAutoRotateScreen;
    private int mHeightOrWidth = -1;
    private int mLastTextureCameraId = -1;

    private MyOrientationEventListener mOrientationListener;
    // The degrees of the device rotated clockwise from its natural orientation.
    private int mLastRawOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;

    private MediaSaveService mMediaSaveService;
    private ServiceConnection mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder b) {
                mMediaSaveService = ((MediaSaveService.LocalBinder) b).getService();
                mCurrentModule.onMediaSaveServiceConnected(mMediaSaveService);
            }
            @Override
            public void onServiceDisconnected(ComponentName className) {
                mMediaSaveService = null;
            }};

    private static final String TAG = "CAM_activity";

    private static final int[] DRAW_IDS = {
            R.drawable.ic_switch_camera,
            R.drawable.ic_switch_video,
            R.drawable.ic_switch_pan,
            R.drawable.ic_switch_photosphere
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.camera_main);
        mFrame = (FrameLayout) findViewById(R.id.camera_app_root);
        mDrawables = new Drawable[DRAW_IDS.length];
        for (int i = 0; i < DRAW_IDS.length; i++) {
            mDrawables[i] = getResources().getDrawable(DRAW_IDS[i]);
        }
        init();
        if (MediaStore.INTENT_ACTION_VIDEO_CAMERA.equals(getIntent().getAction())
                || MediaStore.ACTION_VIDEO_CAPTURE.equals(getIntent().getAction())) {
            mCurrentModule = new VideoModule();
            mCurrentModuleIndex = VIDEO_MODULE_INDEX;
        } else {
            mCurrentModule = new PhotoModule();
            mCurrentModuleIndex = PHOTO_MODULE_INDEX;
        }
        mCurrentModule.init(this, mFrame, true);
        mSwitcher.setCurrentIndex(mCurrentModuleIndex);
        mOrientationListener = new MyOrientationEventListener(this);
        bindMediaSaveService();
    }

    public void init() {
        boolean landscape = Util.getDisplayRotation(this) % 180 == 90;
        mControlsBackground = findViewById(R.id.blocker);
        mCameraControls = findViewById(R.id.camera_controls);
        mShutter = (ShutterButton) findViewById(R.id.shutter_button);
        mSwitcher = (CameraSwitcher) findViewById(R.id.camera_switcher);
        mPieMenuButton = findViewById(R.id.menu);
        int totaldrawid = (LightCycleHelper.hasLightCycleCapture(this)
                                ? DRAW_IDS.length : DRAW_IDS.length - 1);
        if (!ApiHelper.HAS_OLD_PANORAMA) totaldrawid--;

        int[] drawids = new int[totaldrawid];
        int[] moduleids = new int[totaldrawid];
        int ix = 0;
        for (int i = 0; i < mDrawables.length; i++) {
            if (i == PANORAMA_MODULE_INDEX && !ApiHelper.HAS_OLD_PANORAMA) {
                continue; // not enabled, so don't add to UI
            }
            if (i == LIGHTCYCLE_MODULE_INDEX && !LightCycleHelper.hasLightCycleCapture(this)) {
                continue; // not enabled, so don't add to UI
            }
            moduleids[ix] = i;
            drawids[ix++] = DRAW_IDS[i];
        }
        mSwitcher.setIds(moduleids, drawids);
        mSwitcher.setSwitchListener(this);
        mSwitcher.setCurrentIndex(mCurrentModuleIndex);
    }

    @Override
    public void onDestroy() {
        unbindMediaSaveService();
        super.onDestroy();
    }

    // Return whether the auto-rotate screen in system settings
    // is turned on.
    public boolean isAutoRotateScreen() {
        return mAutoRotateScreen;
    }

    private class MyOrientationEventListener
            extends OrientationEventListener {
        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mLastRawOrientation = orientation;
            mCurrentModule.onOrientationChanged(orientation);
        }
    }

    private ObjectAnimator mCameraSwitchAnimator;

    @Override
    public void onCameraSelected(final int i) {
        if (mPaused) return;
        if (i != mCurrentModuleIndex) {
            mPaused = true;
            CameraScreenNail screenNail = getCameraScreenNail();
            if (screenNail != null) {
                if (mCameraSwitchAnimator != null && mCameraSwitchAnimator.isRunning()) {
                    mCameraSwitchAnimator.cancel();
                }
                mCameraSwitchAnimator = ObjectAnimator.ofFloat(
                        screenNail, "alpha", screenNail.getAlpha(), 0f).setDuration(5);
                mCameraSwitchAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        doChangeCamera(i);
                    }
                });
                mCameraSwitchAnimator.start();
            } else {
                doChangeCamera(i);
            }
        }
    }

    private void doChangeCamera(int i) {
        boolean canReuse = canReuseScreenNail();
        CameraHolder.instance().keep();
        closeModule(mCurrentModule);
        mCurrentModuleIndex = i;
        mLastTextureCameraId = -1;
        switch (i) {
            case VIDEO_MODULE_INDEX:
                mCurrentModule = new VideoModule();
                break;
            case PHOTO_MODULE_INDEX:
                mCurrentModule = new PhotoModule();
                break;
            case PANORAMA_MODULE_INDEX:
                mCurrentModule = new PanoramaModule();
                break;
            case LIGHTCYCLE_MODULE_INDEX:
                mCurrentModule = LightCycleHelper.createPanoramaModule();
                break;
        }
        showPieMenuButton(mCurrentModule.needsPieMenu());

        openModule(mCurrentModule, canReuse);
        mCurrentModule.onOrientationChanged(mLastRawOrientation);
        if (mMediaSaveService != null) {
            mCurrentModule.onMediaSaveServiceConnected(mMediaSaveService);
        }
        getCameraScreenNail().setAlpha(0f);
        getCameraScreenNail().setOnFrameDrawnOneShot(mOnFrameDrawn);
    }

    public void showPieMenuButton(boolean show) {
        if (show) {
            findViewById(R.id.blocker).setVisibility(View.VISIBLE);
            findViewById(R.id.menu).setVisibility(View.VISIBLE);
            findViewById(R.id.on_screen_indicators).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.blocker).setVisibility(View.INVISIBLE);
            findViewById(R.id.menu).setVisibility(View.INVISIBLE);
            findViewById(R.id.on_screen_indicators).setVisibility(View.INVISIBLE);
        }
    }

    private Runnable mOnFrameDrawn = new Runnable() {

        @Override
        public void run() {
            runOnUiThread(mFadeInCameraScreenNail);
        }
    };

    private Runnable mFadeInCameraScreenNail = new Runnable() {

        @Override
        public void run() {
            mCameraSwitchAnimator = ObjectAnimator.ofFloat(
                    getCameraScreenNail(), "alpha", 0f, 1f).setDuration(5);
            mCameraSwitchAnimator.setStartDelay(5);
            mCameraSwitchAnimator.start();
        }
    };

    @Override
    public void onShowSwitcherPopup() {
        mCurrentModule.onShowSwitcherPopup();
    }

    private void openModule(CameraModule module, boolean canReuse) {
        module.init(this, mFrame, canReuse && canReuseScreenNail());
        mPaused = false;
        module.onResumeBeforeSuper();
        module.onResumeAfterSuper();
    }

    private void closeModule(CameraModule module) {
        module.onPauseBeforeSuper();
        module.onPauseAfterSuper();
        mFrame.removeAllViews();
    }

    public ShutterButton getShutterButton() {
        return mShutter;
    }

    public void hideUI() {
        mCameraControls.setVisibility(View.INVISIBLE);
        hideSwitcher();
        mShutter.setVisibility(View.GONE);
    }

    public void showUI() {
        mCameraControls.setVisibility(View.VISIBLE);
        showSwitcher();
        mShutter.setVisibility(View.VISIBLE);
        // Force a layout change to show shutter button
        mShutter.requestLayout();
    }

    public void hideSwitcher() {
        mSwitcher.closePopup();
        mSwitcher.setVisibility(View.INVISIBLE);
    }

    public void showSwitcher() {
        if (mCurrentModule.needsSwitcher()) {
            mSwitcher.setVisibility(View.VISIBLE);
        }
    }

    public boolean isInCameraApp() {
        return mShowCameraAppView;
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        mCurrentModule.onConfigurationChanged(config);
    }

    @Override
    public void onPause() {
        if (mCameraSwitchAnimator != null && mCameraSwitchAnimator.isRunning()) {
            mCameraSwitchAnimator.cancel();
        }
        mPaused = true;
        mOrientationListener.disable();
        mCurrentModule.onPauseBeforeSuper();
        super.onPause();
        mCurrentModule.onPauseAfterSuper();
    }

    @Override
    public void onResume() {
        mPaused = false;
        if (Settings.System.getInt(getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0) == 0) {// auto-rotate off
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            mAutoRotateScreen = false;
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            mAutoRotateScreen = true;
        }
        mOrientationListener.enable();
        mCurrentModule.onResumeBeforeSuper();
        super.onResume();
        mCurrentModule.onResumeAfterSuper();
    }

    private void bindMediaSaveService() {
        Intent intent = new Intent(this, MediaSaveService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindMediaSaveService() {
        if (mMediaSaveService != null) {
            mMediaSaveService.setListener(null);
        }
        if (mConnection != null) {
            unbindService(mConnection);
        }
    }

    @Override
    protected void onFullScreenChanged(boolean full) {
        if (full) {
            showUI();
        } else {
            hideUI();
        }
        super.onFullScreenChanged(full);
        if (ApiHelper.HAS_ROTATION_ANIMATION) {
            setRotationAnimation(full);
        }
        mCurrentModule.onFullScreenChanged(full);
    }

    private void setRotationAnimation(boolean fullscreen) {
        int rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE;
        if (fullscreen) {
            rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE;
        }
        Window win = getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        winParams.rotationAnimation = rotationAnimation;
        win.setAttributes(winParams);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mCurrentModule.onStop();
        getStateManager().clearTasks();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getStateManager().clearActivityResult();
    }

    @Override
    protected void installIntentFilter() {
        super.installIntentFilter();
        mCurrentModule.installIntentFilter();
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        // Only PhotoPage understands ProxyLauncher.RESULT_USER_CANCELED
        if (resultCode == ProxyLauncher.RESULT_USER_CANCELED
                && !(getStateManager().getTopState() instanceof PhotoPage)) {
            resultCode = RESULT_CANCELED;
        }
        super.onActivityResult(requestCode, resultCode, data);
        // Unmap cancel vs. reset
        if (resultCode == ProxyLauncher.RESULT_USER_CANCELED) {
            resultCode = RESULT_CANCELED;
        }
        mCurrentModule.onActivityResult(requestCode, resultCode, data);
    }

    // Preview area is touched. Handle touch focus.
    // Touch to focus is handled by PreviewGestures, this function call
    // is no longer needed. TODO: Clean it up in the next refactor
    @Override
    protected void onSingleTapUp(View view, int x, int y) {
    }

    @Override
    public void onBackPressed() {
        if (!mCurrentModule.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return mCurrentModule.onKeyDown(keyCode,  event)
                || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return mCurrentModule.onKeyUp(keyCode,  event)
                || super.onKeyUp(keyCode, event);
    }

    public void cancelActivityTouchHandling() {
        if (mDown != null) {
            MotionEvent cancel = MotionEvent.obtain(mDown);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        if (m.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mDown = m;
        }
        if ((mSwitcher != null) && mSwitcher.showsPopup() && !mSwitcher.isInsidePopup(m)) {
            return mSwitcher.onTouch(null, m);
        } else if ((mSwitcher != null) && mSwitcher.isInsidePopup(m)) {
            return superDispatchTouchEvent(m);
        } else {
            return mCurrentModule.dispatchTouchEvent(m);
        }
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        Intent proxyIntent = new Intent(this, ProxyLauncher.class);
        proxyIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        proxyIntent.putExtra(Intent.EXTRA_INTENT, intent);
        super.startActivityForResult(proxyIntent, requestCode);
    }

    public boolean superDispatchTouchEvent(MotionEvent m) {
        return super.dispatchTouchEvent(m);
    }

    // Preview texture has been copied. Now camera can be released and the
    // animation can be started.
    @Override
    public void onPreviewTextureCopied() {
        mCurrentModule.onPreviewTextureCopied();
    }

    @Override
    public void onCaptureTextureCopied() {
        mCurrentModule.onCaptureTextureCopied();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        mCurrentModule.onUserInteraction();
    }

    @Override
    protected boolean updateStorageHintOnResume() {
        return mCurrentModule.updateStorageHintOnResume();
    }

    @Override
    public void updateCameraAppView() {
        super.updateCameraAppView();
        mCurrentModule.updateCameraAppView();
    }

    private boolean canReuseScreenNail() {
        return mCurrentModuleIndex == PHOTO_MODULE_INDEX
                || mCurrentModuleIndex == VIDEO_MODULE_INDEX
                || mCurrentModuleIndex == LIGHTCYCLE_MODULE_INDEX;
    }

    @Override
    public boolean isPanoramaActivity() {
        return (mCurrentModuleIndex == PANORAMA_MODULE_INDEX);
    }

    public SurfaceTexture getScreenNailTextureForPreviewSize(int cameraId,
            int displayOrientation, Camera.Parameters parameters) {
        CameraScreenNail screenNail = getCameraScreenNail();
        Camera.Size size = parameters.getPreviewSize();
        int previewWidth, previewHeight;

        if ((displayOrientation % 180) != 0) {
            previewWidth = size.height;
            previewHeight = size.width;
        } else {
            previewWidth = size.width;
            previewHeight = size.height;
        }

        if (screenNail.getSurfaceTexture() == null || mLastTextureCameraId != cameraId) {
            mLastTextureCameraId = cameraId;
            screenNail.releaseSurfaceTexture();
            screenNail.setSize(previewWidth, previewHeight);
            screenNail.enableAspectRatioClamping();
            notifyScreenNailChanged();
            screenNail.acquireSurfaceTexture();
        } else {
            if (screenNail.getTextureWidth() != previewWidth || screenNail.getTextureHeight() != previewHeight) {
                screenNail.setSize(previewWidth, previewHeight);
            }
            screenNail.enableAspectRatioClamping();
            notifyScreenNailChanged();
        }

        return screenNail.getSurfaceTexture();
    }

    // Accessor methods for getting latency times used in performance testing
    public long getAutoFocusTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mAutoFocusTime : -1;
    }

    public long getShutterLag() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mShutterLag : -1;
    }

    public long getShutterToPictureDisplayedTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mShutterToPictureDisplayedTime : -1;
    }

    public long getPictureDisplayedToJpegCallbackTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mPictureDisplayedToJpegCallbackTime : -1;
    }

    public long getJpegCallbackFinishTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mJpegCallbackFinishTime : -1;
    }

    public long getCaptureStartTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mCaptureStartTime : -1;
    }

    public boolean isRecording() {
        return (mCurrentModule instanceof VideoModule) ?
                ((VideoModule) mCurrentModule).isRecording() : false;
    }

    public CameraScreenNail getCameraScreenNail() {
        return (CameraScreenNail) mCameraScreenNail;
    }

    public MediaSaveService getMediaSaveService() {
        return mMediaSaveService;
    }
}
