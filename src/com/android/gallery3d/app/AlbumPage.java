/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
//import android.drm.DrmHelper;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.filtershow.crop.CropActivity;
import com.android.gallery3d.filtershow.crop.CropExtras;
import com.android.gallery3d.glrenderer.FadeTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.ui.ActionModeHandler;
import com.android.gallery3d.ui.ActionModeHandler.ActionModeListener;
import com.android.gallery3d.ui.AlbumSlotRenderer;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.DetailsHelper.CloseListener;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.PhotoFallbackEffect;
import com.android.gallery3d.ui.RelativePosition;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.SlotView;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.MediaSetUtils;

import java.util.ArrayList;
import java.util.Locale;

public class AlbumPage extends ActivityState implements GalleryActionBar.ClusterRunner,
        SelectionManager.SelectionListener, MediaSet.SyncListener, GalleryActionBar.OnAlbumModeSelectedListener {
    @SuppressWarnings("unused")
    private static final String TAG = "AlbumPage";

    public static final String KEY_MEDIA_PATH = "media-path";
    public static final String KEY_PARENT_MEDIA_PATH = "parent-media-path";
    public static final String KEY_SET_CENTER = "set-center";
    public static final String KEY_AUTO_SELECT_ALL = "auto-select-all";
    public static final String KEY_SHOW_CLUSTER_MENU = "cluster-menu";
    public static final String KEY_EMPTY_ALBUM = "empty-album";
    public static final String KEY_RESUME_ANIMATION = "resume_animation";
    public static final String KEY_IS_VIDEOS_SCREEN = "is-videos-screen";
    public static final String KEY_VIEWTYPE = "viewtype";

    private static final int REQUEST_SLIDESHOW = 1;
    public static final int REQUEST_PHOTO = 2;
    private static final int REQUEST_DO_ANIMATION = 3;

    private static final int BIT_LOADING_RELOAD = 1;
    private static final int BIT_LOADING_SYNC = 2;

    private static final float USER_DISTANCE_METER = 0.3f;

    // Data cache size, equal to AlbumDataLoader.DATA_CACHE_SIZE
    private static final int DATA_CACHE_SIZE = 256;

    private boolean mIsActive = false;
    private AlbumSlotRenderer mAlbumView;
    private Path mMediaSetPath;
    private String mParentMediaSetString;
    private SlotView mSlotView;

    private AlbumDataLoader mAlbumDataAdapter;

    protected SelectionManager mSelectionManager;

    private boolean mGetContent;
    //private boolean mShowClusterMenu;
    private boolean mIsVideoScreen;

    private ActionModeHandler mActionModeHandler;
    private int mFocusIndex = 0;
    private DetailsHelper mDetailsHelper;
    private MyDetailsSource mDetailsSource;
    private MediaSet mMediaSet;
    private boolean mShowDetails;
    private float mUserDistance; // in pixel
    private Future<Integer> mSyncTask = null;
    private boolean mLaunchedFromPhotoPage;
    private boolean mInCameraApp;
    private boolean mInCameraAndWantQuitOnPause;

    private int mLoadingBits = 0;
    private boolean mInitialSynced = false;
    private int mSyncResult;
    private boolean mLoadingFailed;
    private RelativePosition mOpenCenter = new RelativePosition();

    private Handler mHandler;
    private static final int MSG_PICK_PHOTO = 0;

    private Menu mActionMenu;

    private PhotoFallbackEffect mResumeEffect;

    private Config.AlbumPage mConfig;
    private Config.AlbumPageList mConfigList;
    private GalleryActionBar mActionBar;

    public static final int GRID_VIEW = 0;
    public static final int LIST_VIEW = 1;
    public static int mCurrentView = GRID_VIEW;
    private final String PREF_VIEWTYPE = "albumview-type";
    public boolean mViewType = true;
    private Bundle mData;
    private MenuItem mItemViewType;
    private TextView tvEmptyAlbum;
    private boolean mShowedEmptyToastForSelf;
    private PhotoFallbackEffect.PositionProvider mPositionProvider =
            new PhotoFallbackEffect.PositionProvider() {
        @Override
        public Rect getPosition(int index) {
            Rect rect = mSlotView.getSlotRect(index);
            Rect bounds = mSlotView.bounds();
            rect.offset(bounds.left - mSlotView.getScrollX(),
                    bounds.top - mSlotView.getScrollY());
            return rect;
        }

        @Override
        public int getItemIndex(Path path) {
            int start = mSlotView.getVisibleStart();
            int end = mSlotView.getVisibleEnd();
            for (int i = start; i < end; ++i) {
                MediaItem item = mAlbumDataAdapter.get(i);
                if (item != null && item.getPath() == path) return i;
            }
            return -1;
        }
    };

    @Override
    protected int getBackgroundColorId() {
        return R.color.album_background;
    }

    private final GLView mRootPane = new GLView() {
        private final float mMatrix[] = new float[16];

        @Override
        protected void onLayout(
                boolean changed, int left, int top, int right, int bottom) {

            int paddingLeft;
            int paddingBottom;
            int paddingRight;
            int paddingTop;

            if (mViewType) {
                paddingTop = mConfig.paddingLeft;
                paddingLeft = mConfig.paddingLeft;
            } else {
                paddingTop = mConfigList.paddingLeft;
                paddingLeft = mConfigList.paddingLeft;
            }
            paddingBottom = mConfig.paddingBottom;
            paddingRight = mConfig.paddingRight;

            int slotViewTop;
            Toolbar toolbar = mActivity.getToolbar();
            if (toolbar != null) {
                slotViewTop = toolbar.getLayoutParams().height + paddingTop;
            } else {
                slotViewTop = mActivity.getGalleryActionBar().getHeight() + paddingTop;
            }
            int slotViewBottom = bottom - top - paddingBottom;
            int slotViewRight = right - left - paddingRight;
            int slotViewLeft = paddingLeft;

            if (mShowDetails) {
                mDetailsHelper.layout(slotViewLeft, slotViewTop, slotViewRight, slotViewBottom);
            } else {
                mAlbumView.setHighlightItemPath(null);
            }

            // Set the mSlotView as a reference point to the open animation
            mOpenCenter.setReferencePosition(0, slotViewTop);
            mSlotView.layout(slotViewLeft, slotViewTop, slotViewRight,
                    slotViewBottom);

            GalleryUtils.setViewPointMatrix(mMatrix, (right - left) / 2,
                    (bottom - top) / 2, -mUserDistance);
        }

        @Override
        protected void render(GLCanvas canvas) {
            canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
            canvas.multiplyMatrix(mMatrix, 0);
            super.render(canvas);

            if (mResumeEffect != null) {
                boolean more = mResumeEffect.draw(canvas);
                if (!more) {
                    mResumeEffect = null;
                    mAlbumView.setSlotFilter(null);
                }
                // We want to render one more time even when no more effect
                // required. So that the animated thumbnails could be draw
                // with declarations in super.render().
                invalidate();
            }
            canvas.restore();
        }
    };

    // This are the transitions we want:
    //
    // +--------+           +------------+    +-------+    +----------+
    // | Camera |---------->| Fullscreen |--->| Album |--->| AlbumSet |
    // |  View  | thumbnail |   Photo    | up | Page  | up |   Page   |
    // +--------+           +------------+    +-------+    +----------+
    //     ^                      |               |            ^  |
    //     |                      |               |            |  |         close
    //     +----------back--------+               +----back----+  +--back->  app
    //
    @Override
    protected void onBackPressed() {
        if (mShowDetails) {
            hideDetails();
        } else if (mSelectionManager.inSelectionMode()) {
            mSelectionManager.leaveSelectionMode();
        } else if (mIsVideoScreen) {
            super.onBackPressed();
        } else {
            if(mLaunchedFromPhotoPage) {
                mActivity.getTransitionStore().putIfNotPresent(
                        PhotoPage.KEY_ALBUMPAGE_TRANSITION,
                        PhotoPage.MSG_ALBUMPAGE_RESUMED);
            }
            // TODO: fix this regression
            // mAlbumView.savePositions(PositionRepository.getInstance(mActivity));
            if (mInCameraApp) {
                super.onBackPressed();
            } else {
                onUpPressed();
            }
        }
    }

    private void onUpPressed() {
        if (mInCameraApp) {
            GalleryUtils.startGalleryActivity(mActivity);
        } else if (mActivity.getStateManager().getStateCount() > 1) {
            ((GalleryActivity) mActivity).toggleNavBar(true);
            super.onBackPressed();
        } else if (mParentMediaSetString != null) {
            Bundle data = new Bundle(getData());
            data.putString(AlbumSetPage.KEY_MEDIA_PATH, mParentMediaSetString);
            mActivity.getStateManager().switchState(
                    this, AlbumSetPage.class, data);
        }
    }

    private void onDown(int index) {
        mAlbumView.setPressedIndex(index);
    }

    private void onUp(boolean followedByLongPress) {
        if (followedByLongPress) {
            // Avoid showing press-up animations for long-press.
            mAlbumView.setPressedIndex(-1);
        } else {
            mAlbumView.setPressedUp();
        }
    }

    private void onSingleTapUp(int slotIndex) {
        if (!mIsActive) return;

        if (mSelectionManager.inSelectionMode()) {
            MediaItem item = mAlbumDataAdapter.get(slotIndex);
            if (item == null) return; // Item not ready yet, ignore the click
            mSelectionManager.toggle(item.getPath());
            mSlotView.invalidate();
        } else {
            // Render transition in pressed state
            mAlbumView.setPressedIndex(slotIndex);
            mAlbumView.setPressedUp();
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PICK_PHOTO, slotIndex, 0),
                    FadeTexture.DURATION);
        }
    }

    private void pickPhoto(int slotIndex) {
        pickPhoto(slotIndex, false);
    }

    private void pickPhoto(int slotIndex, boolean startInFilmstrip) {
        if (!mIsActive) return;

        if (!startInFilmstrip) {
            // Launch photos in lights out mode
            mActivity.getGLRoot().setLightsOutMode(true);
        }

        MediaItem item = mAlbumDataAdapter.get(slotIndex);

        // Checking it is RTL or not
        boolean isLayoutRtl = (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) ? true : false;

        // When not RTL, return directly to ignore the click
        if (!isLayoutRtl && item == null) {
            return;
        }

        if (mGetContent) {
            if (isLayoutRtl && item == null) {
                return; // Item not ready yet, ignore the click
            }
//            if (DrmHelper.isDrmFile(DrmHelper.getFilePath(
//                    mActivity.getAndroidContext(), item.getContentUri()))) {
//                Toast.makeText(mActivity, R.string.no_permission_for_drm,
//                        Toast.LENGTH_SHORT).show();
//                return;
//            }
            onGetContent(item);
        } else if (mLaunchedFromPhotoPage) {
            if (isLayoutRtl && item == null) {
                return; // Item not ready yet, ignore the click
            }
            TransitionStore transitions = mActivity.getTransitionStore();
            transitions.put(
                    PhotoPage.KEY_ALBUMPAGE_TRANSITION,
                    PhotoPage.MSG_ALBUMPAGE_PICKED);
            transitions.put(PhotoPage.KEY_INDEX_HINT, slotIndex);
            onBackPressed();
        } else {
            // Get into the PhotoPage.
            // mAlbumView.savePositions(PositionRepository.getInstance(mActivity));
            Bundle data = new Bundle();
            data.putInt(PhotoPage.KEY_INDEX_HINT, slotIndex);
            data.putParcelable(PhotoPage.KEY_OPEN_ANIMATION_RECT,
                    mSlotView.getSlotRect(slotIndex, mRootPane));
            data.putString(PhotoPage.KEY_MEDIA_SET_PATH,
                    mMediaSetPath.toString());

            // Item not ready yet, don't pass the photo path to bundle
            if (!isLayoutRtl && item != null) {
                data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH,
                        item.getPath().toString());
            }
            data.putInt(PhotoPage.KEY_ALBUMPAGE_TRANSITION,
                    PhotoPage.MSG_ALBUMPAGE_STARTED);
            data.putBoolean(PhotoPage.KEY_START_IN_FILMSTRIP,
                    startInFilmstrip);
            data.putBoolean(PhotoPage.KEY_IN_CAMERA_ROLL, mMediaSet.isCameraRoll());
            data.putBoolean(PhotoPage.KEY_FROM_VIDEOS_SCREEN, mIsVideoScreen);
            if (startInFilmstrip) {
                mActivity.getStateManager().switchState(this, FilmstripPage.class, data);
            } else {
                mActivity.getStateManager().startStateForResult(
                            SinglePhotoPage.class, REQUEST_PHOTO, data);
            }
        }
    }

    private void onGetContent(final MediaItem item) {
        DataManager dm = mActivity.getDataManager();
        Activity activity = mActivity;
        if (mData.getString(GalleryActivity.EXTRA_CROP) != null) {
            Uri uri = dm.getContentUri(item.getPath());
            Intent intent = new Intent(CropActivity.CROP_ACTION, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                    .putExtras(getData());
            if (mData.getParcelable(MediaStore.EXTRA_OUTPUT) == null) {
                intent.putExtra(CropExtras.KEY_RETURN_DATA, true);
            }
            activity.startActivity(intent);
            activity.finish();
        } else {
            Intent intent = new Intent(null, item.getContentUri())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.setResult(Activity.RESULT_OK, intent);
            activity.finish();
        }
    }

    public void onLongTap(int slotIndex) {
        if (mGetContent) return;
        MediaItem item = mAlbumDataAdapter.get(slotIndex);
        if (item == null) return;
        mSelectionManager.setAutoLeaveSelectionMode(true);
        mSelectionManager.toggle(item.getPath());
        mSlotView.invalidate();
    }

    @Override
    public void doCluster(int clusterType) {
        String basePath = mMediaSet.getPath().toString();
        String newPath = FilterUtils.newClusterPath(basePath, clusterType);
        Bundle data = new Bundle(getData());
        data.putString(AlbumSetPage.KEY_MEDIA_PATH, newPath);
        mActivity.getStateManager().startStateForResult(
                AlbumSetPage.class, REQUEST_DO_ANIMATION, data);
    }

    @Override
    protected void onCreate(Bundle data, Bundle restoreState) {
        super.onCreate(data, restoreState);
        mUserDistance = GalleryUtils.meterToPixel(USER_DISTANCE_METER);
        mCurrentView = GalleryUtils.getIntPref(mActivity, PREF_VIEWTYPE,
                GRID_VIEW);
        mViewType = mCurrentView == GRID_VIEW;
        mData = data;
        initializeViews();
        initializeData(data);
        mGetContent = data.getBoolean(GalleryActivity.KEY_GET_CONTENT, false);
        mIsVideoScreen = data.getBoolean(KEY_IS_VIDEOS_SCREEN, false);
        mDetailsSource = new MyDetailsSource();
        Context context = mActivity.getAndroidContext();
        mActionBar = mActivity.getGalleryActionBar();

        if (data.getBoolean(KEY_AUTO_SELECT_ALL)) {
            mSelectionManager.selectAll();
        }

        mLaunchedFromPhotoPage =
                mActivity.getStateManager().hasStateClass(FilmstripPage.class);
        mInCameraApp = data.getBoolean(PhotoPage.KEY_APP_BRIDGE, false);

        mHandler = new SynchronizedHandler(mActivity.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_PICK_PHOTO: {
                        pickPhoto(message.arg1);
                        break;
                    }
                    default:
                        throw new AssertionError(message.what);
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsActive = true;

        mResumeEffect = mActivity.getTransitionStore().get(KEY_RESUME_ANIMATION);
        if (mResumeEffect != null) {
            mAlbumView.setSlotFilter(mResumeEffect);
            mResumeEffect.setPositionProvider(mPositionProvider);
            mResumeEffect.start();
        }

        setContentPane(mRootPane);

        // Set the reload bit here to prevent it exit this page in clearLoadingBit().
        setLoadingBit(BIT_LOADING_RELOAD);
        mLoadingFailed = false;
        mAlbumDataAdapter.resume();

        mAlbumView.resume();
        mAlbumView.setPressedIndex(-1);
        mActionModeHandler.resume();
        if (!mInitialSynced) {
            setLoadingBit(BIT_LOADING_SYNC);
            mSyncTask = mMediaSet.requestSync(this);
        }
        mInCameraAndWantQuitOnPause = mInCameraApp;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsActive = false;

        if (mSelectionManager.inSelectionMode()) {
            mSelectionManager.leaveSelectionMode();
        }
        mAlbumView.setSlotFilter(null);
        mActionModeHandler.pause();
        mAlbumDataAdapter.pause();
        mAlbumView.pause();
        DetailsHelper.pause();

        if (mSyncTask != null) {
            mSyncTask.cancel();
            mSyncTask = null;
            clearLoadingBit(BIT_LOADING_SYNC);
        }
        hideEmptyAlbumToast();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAlbumDataAdapter != null) {
            mAlbumDataAdapter.setLoadingListener(null);
        }
        mActionModeHandler.destroy();
    }

    private void initializeViews() {
        mSelectionManager = new SelectionManager(mActivity, false);
        mSelectionManager.setSelectionListener(this);
        mConfig = Config.AlbumPage.get(mActivity);
        mConfigList = Config.AlbumPageList.get(mActivity);
        if (mViewType) {
            mSlotView = new SlotView(mActivity, mConfig.slotViewSpec);
            mAlbumView = new AlbumSlotRenderer(mActivity, mSlotView,
                    mConfig.labelSpec, mSelectionManager, mConfig.placeholderColor, mViewType);
        } else {
            mSlotView = new SlotView(mActivity, mConfigList.slotViewSpec);
            mAlbumView = new AlbumSlotRenderer(mActivity, mSlotView,
                    mConfigList.labelSpec, mSelectionManager,
                    mConfig.placeholderColor, mViewType);
        }
        mSlotView.setSlotRenderer(mAlbumView);
        mRootPane.addComponent(mSlotView);
        mSlotView.setListener(new SlotView.SimpleListener() {
            @Override
            public void onDown(int index) {
                AlbumPage.this.onDown(index);
            }

            @Override
            public void onUp(boolean followedByLongPress) {
                AlbumPage.this.onUp(followedByLongPress);
            }

            @Override
            public void onSingleTapUp(int slotIndex) {
                AlbumPage.this.onSingleTapUp(slotIndex);
            }

            @Override
            public void onLongTap(int slotIndex) {
                AlbumPage.this.onLongTap(slotIndex);
            }
        });
        mActionModeHandler = new ActionModeHandler(mActivity, mSelectionManager);
        mActionModeHandler.setActionModeListener(new ActionModeListener() {
            @Override
            public boolean onActionItemClicked(MenuItem item) {
                return onItemSelected(item);
            }
        });
    }

    private void initializeData(Bundle data) {
        mMediaSetPath = Path.fromString(data.getString(KEY_MEDIA_PATH));
        mParentMediaSetString = data.getString(KEY_PARENT_MEDIA_PATH);
        mMediaSet = mActivity.getDataManager().getMediaSet(mMediaSetPath);
        if (mMediaSet == null) {
            Utils.fail("MediaSet is null. Path = %s", mMediaSetPath);
        }
        mSelectionManager.setSourceMediaSet(mMediaSet);
        mAlbumDataAdapter = new AlbumDataLoader(mActivity, mMediaSet);
        mAlbumDataAdapter.setLoadingListener(new MyLoadingListener());
        mAlbumView.setModel(mAlbumDataAdapter);
    }

    private void showDetails() {
        mShowDetails = true;
        if (mDetailsHelper == null) {
            mDetailsHelper = new DetailsHelper(mActivity, mRootPane, mDetailsSource);
            mDetailsHelper.setCloseListener(new CloseListener() {
                @Override
                public void onClose() {
                    hideDetails();
                }
            });
        }
        mDetailsHelper.show();
    }

    private void hideDetails() {
        mShowDetails = false;
        mDetailsHelper.hide();
        mAlbumView.setHighlightItemPath(null);
        mSlotView.invalidate();
    }

    @Override
    protected boolean onCreateActionBar(Menu menu) {
        mActionMenu = menu;
        //GalleryActionBar actionBar = mActivity.getGalleryActionBar();
        MenuInflater inflator = getSupportMenuInflater();
        if (mGetContent) {
            inflator.inflate(R.menu.pickup, menu);
            int typeBits = mData.getInt(GalleryActivity.KEY_TYPE_BITS,
                    DataManager.INCLUDE_IMAGE);
            mActionBar.setTitle(GalleryUtils.getSelectionModePrompt(typeBits));
        } else {
            inflator.inflate(R.menu.album, menu);
            mActionBar.setTitle(mMediaSet.getName());

            FilterUtils.setupMenuItems(mActionBar, mMediaSetPath, true);

            menu.findItem(R.id.action_camera).setVisible(
                   GalleryUtils.isAnyCameraAvailable(mActivity));
            menu.findItem(R.id.action_slideshow).setVisible(!mIsVideoScreen);
            MenuItem item = menu.findItem(R.id.action_view_type);
            updateMenuTitle(item);

        }
        //actionBar.setSubtitle(null);
        // remove slideshow if all are videos
        if (allVideoFiles() && !mGetContent){
            menu.findItem(R.id.action_slideshow).setVisible(false);
        }
        return true;
    }

    private boolean allVideoFiles() {
        if (mMediaSet == null)
            return false;
        int count = mMediaSet.getMediaItemCount();
        MediaItem item;
        for (int i = 0; i < count; i++) {
            item = mMediaSet.getMediaItem(i, 1).get(0);
            if (item == null) {
                continue;
            }
            if (item.getMimeType().trim().startsWith("image/"))
                return false;
        }
        return true;
    }

    private void prepareAnimationBackToFilmstrip(int slotIndex) {
        if (mAlbumDataAdapter == null || !mAlbumDataAdapter.isActive(slotIndex)) return;
        MediaItem item = mAlbumDataAdapter.get(slotIndex);
        if (item == null) return;
        TransitionStore transitions = mActivity.getTransitionStore();
        transitions.put(PhotoPage.KEY_INDEX_HINT, slotIndex);
        transitions.put(PhotoPage.KEY_OPEN_ANIMATION_RECT,
                mSlotView.getSlotRect(slotIndex, mRootPane));
    }

    private void switchToFilmstrip() {
        // Invalid album, return back directly.
        if (mAlbumDataAdapter.size() < 1) {
            return;
        }

        int targetPhoto = mSlotView.getVisibleStart();
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            // Fetch corresponding index from another side, only in RTL
            targetPhoto = mAlbumDataAdapter.size() - targetPhoto - 1;
        }
        prepareAnimationBackToFilmstrip(targetPhoto);
        if(mLaunchedFromPhotoPage) {
            onBackPressed();
        } else {
            pickPhoto(targetPhoto, true);
        }
    }

    @Override
    protected boolean onItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onUpPressed();
                return true;
            }
            case R.id.action_cancel:
                mActivity.getStateManager().finishState(this);
                return true;
            case R.id.action_select:
                mSelectionManager.setAutoLeaveSelectionMode(false);
                mSelectionManager.enterSelectionMode();
                return true;
            case R.id.action_slideshow: {
                mInCameraAndWantQuitOnPause = false;
                Bundle data = new Bundle();
                data.putString(SlideshowPage.KEY_SET_PATH,
                        mMediaSetPath.toString());
                data.putBoolean(SlideshowPage.KEY_REPEAT, true);
                mActivity.getStateManager().startStateForResult(
                        SlideshowPage.class, REQUEST_SLIDESHOW, data);
                return true;
            }
            case R.id.action_details: {
                if (mShowDetails) {
                    hideDetails();
                } else {
                    showDetails();
                }
                return true;
            }
            case R.id.action_camera: {
                GalleryUtils.startCameraActivity(mActivity);
                return true;
            }
            case R.id.action_view_type: {
            switchView();
            return true;
            }
            default:
                return false;
        }
    }

    @Override
    protected void onStateResult(int request, int result, Intent data) {
        switch (request) {
            case REQUEST_SLIDESHOW: {
                // data could be null, if there is no images in the album
                if (data == null) return;
                mFocusIndex = data.getIntExtra(SlideshowPage.KEY_PHOTO_INDEX, 0);
                mSlotView.setCenterIndex(mFocusIndex);
                break;
            }
            case REQUEST_PHOTO: {
                if (data == null) return;
                mFocusIndex = data.getIntExtra(PhotoPage.KEY_RETURN_INDEX_HINT, 0);
                if (View.LAYOUT_DIRECTION_RTL == TextUtils
                        .getLayoutDirectionFromLocale(Locale.getDefault())) {
                    // Fetch corresponding index from another side, only in RTL
                    mFocusIndex = mAlbumDataAdapter.size() - mFocusIndex - 1;
                    // Prepare to jump to mFocusIndex position, only enabled in RTL
                    mSlotView.setIsFromPhotoPage(true);
                }

                // Let picture of mFocusIndex visible
                mSlotView.makeSlotVisible(mFocusIndex);

                if (View.LAYOUT_DIRECTION_RTL == TextUtils
                        .getLayoutDirectionFromLocale(Locale.getDefault())) {
                    // Reset variable
                    mSlotView.setIsFromPhotoPage(false);
                }
                break;
            }
            case REQUEST_DO_ANIMATION: {
                mSlotView.startRisingAnimation();
                break;
            }
        }
    }

    private void updateMenuItem() {
        if (allVideoFiles() && !mGetContent) {
            mActionMenu.findItem(R.id.action_slideshow).setVisible(false);
        }
    }

    @Override
    public void onSelectionModeChange(int mode) {
        switch (mode) {
            case SelectionManager.ENTER_SELECTION_MODE: {
                mActionModeHandler.startActionMode();
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                break;
            }
            case SelectionManager.LEAVE_SELECTION_MODE: {
                mActionModeHandler.finishActionMode();
                mRootPane.invalidate();
                updateMenuItem();
                break;
            }
            case SelectionManager.SELECT_ALL_MODE: {
                mActionModeHandler.updateSupportedOperation();
                mRootPane.invalidate();
                break;
            }
        }
    }

    @Override
    public void onSelectionChange(Path path, boolean selected) {
        int count = mSelectionManager.getSelectedCount();
        String format = mActivity.getResources().getQuantityString(
                R.plurals.number_of_items_selected, count);
        mActionModeHandler.setTitle(String.format(format, count));
        mActionModeHandler.updateSupportedOperation(path, selected);
    }

    @Override
    public void onSyncDone(final MediaSet mediaSet, final int resultCode) {
        Log.d(TAG, "onSyncDone: " + Utils.maskDebugInfo(mediaSet.getName()) + " result="
                + resultCode);
        ((Activity) mActivity).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                GLRoot root = mActivity.getGLRoot();
                root.lockRenderThread();
                mSyncResult = resultCode;
                try {
                    if (resultCode == MediaSet.SYNC_RESULT_SUCCESS) {
                        mInitialSynced = true;
                    }
                    clearLoadingBit(BIT_LOADING_SYNC);
                    showSyncErrorIfNecessary(mLoadingFailed);
                } finally {
                    root.unlockRenderThread();
                }
            }
        });
    }

    // Show sync error toast when all the following conditions are met:
    // (1) both loading and sync are done,
    // (2) sync result is error,
    // (3) the page is still active, and
    // (4) no photo is shown or loading fails.
    private void showSyncErrorIfNecessary(boolean loadingFailed) {
        if ((mLoadingBits == 0) && (mSyncResult == MediaSet.SYNC_RESULT_ERROR) && mIsActive
                && (loadingFailed || (mAlbumDataAdapter.size() == 0))) {
            Toast.makeText(mActivity, R.string.sync_album_error,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void setLoadingBit(int loadTaskBit) {
        mLoadingBits |= loadTaskBit;
    }

    private void clearLoadingBit(int loadTaskBit) {
        mLoadingBits &= ~loadTaskBit;
        if (mLoadingBits == 0 && mIsActive) {
            if (mAlbumDataAdapter.size() == 0) {
                if (mIsVideoScreen) {
                    mShowedEmptyToastForSelf = true;
                    showEmptyAlbumToast(Toast.LENGTH_LONG);
                } else {
                    Intent result = new Intent();
                    result.putExtra(KEY_EMPTY_ALBUM, true);
                    setStateResult(Activity.RESULT_OK, result);
                    mActivity.getStateManager().finishState(this);
                }
                return;
            } else {
                hideEmptyAlbumToast();
            }
        }
        if (mShowedEmptyToastForSelf && mIsVideoScreen) {
            mShowedEmptyToastForSelf = false;
            hideEmptyAlbumToast();
        }
    }

    private class MyLoadingListener implements LoadingListener {
        @Override
        public void onLoadingStarted() {
            setLoadingBit(BIT_LOADING_RELOAD);
            mLoadingFailed = false;
        }

        @Override
        public void onLoadingFinished(boolean loadingFailed) {
            clearLoadingBit(BIT_LOADING_RELOAD);
            mLoadingFailed = loadingFailed;
            showSyncErrorIfNecessary(loadingFailed);
        }
    }

    private class MyDetailsSource implements DetailsHelper.DetailsSource {
        private int mIndex;

        @Override
        public int size() {
            return mAlbumDataAdapter.size();
        }

        @Override
        public int setIndex() {
            Path id = mSelectionManager.getSelected(false).get(0);
            mIndex = mAlbumDataAdapter.findItem(id);
            return mIndex;
        }

        @Override
        public MediaDetails getDetails() {
            // this relies on setIndex() being called beforehand
            MediaObject item = mAlbumDataAdapter.get(mIndex);
            if (item != null) {
                mAlbumView.setHighlightItemPath(item.getPath());
                return item.getDetails();
            } else {
                return null;
            }
        }
    }

    @Override
    public void onAlbumModeSelected(int mode) {
        if (mode == GalleryActionBar.ALBUM_FILMSTRIP_MODE_SELECTED) {
            switchToFilmstrip();
        }
    }

     public void updateMenuTitle(MenuItem item) {

       item.setTitle(mViewType ? R.string.action_viewtype_list
        : R.string.action_viewtype_grid);
    }

    private void switchView() {
        if (mViewType) {
            mCurrentView = LIST_VIEW;
            GalleryUtils.setIntPref(mActivity, PREF_VIEWTYPE, mCurrentView);
            mActivity.getStateManager().switchState(this,
            AlbumListViewPage.class, mData);
        } else {
            mCurrentView = GRID_VIEW;
            GalleryUtils.setIntPref(mActivity, PREF_VIEWTYPE, mCurrentView);
            mActivity.getStateManager().switchState(this, AlbumPage.class,
            mData);
       }

    }


    private void showEmptyAlbumToast(int toastLength) {
        RelativeLayout galleryRoot = (RelativeLayout) mActivity.findViewById(R.id.gallery_root);
        if (galleryRoot == null) return;
        if (tvEmptyAlbum == null) {
            tvEmptyAlbum = new TextView(mActivity);
            tvEmptyAlbum.setText(R.string.tvEmptyVideos);
            tvEmptyAlbum.setTextColor(Color.parseColor("#8A000000"));
            tvEmptyAlbum.setGravity(Gravity.CENTER);
            tvEmptyAlbum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            lp.addRule(RelativeLayout.CENTER_IN_PARENT);
            galleryRoot.addView(tvEmptyAlbum, lp);
        }
        tvEmptyAlbum.setVisibility(View.VISIBLE);
    }

    private void hideEmptyAlbumToast() {
        if (tvEmptyAlbum != null) {
            tvEmptyAlbum.setVisibility(View.GONE);
        }
    }
}
