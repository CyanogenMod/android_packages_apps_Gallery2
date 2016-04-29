/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 * Not a Contribution
 *
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

import java.util.Locale;
import android.os.Handler;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.Manifest;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;
import android.widget.Toolbar.OnMenuItemClickListener;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.DrawerLayout.DrawerListener;
import android.text.TextUtils;

import java.util.ArrayList;
import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.tools.DualCameraNativeEngine;
import com.android.gallery3d.mpo.MpoParser;
import com.android.gallery3d.picasasource.PicasaSource;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;

public final class GalleryActivity extends AbstractGalleryActivity implements OnCancelListener {
    public static final String EXTRA_SLIDESHOW = "slideshow";
    public static final String EXTRA_DREAM = "dream";
    public static final String EXTRA_CROP = "crop";

    public static final String ACTION_REVIEW = "com.android.camera.action.REVIEW";
    public static final String KEY_GET_CONTENT = "get-content";
    public static final String KEY_GET_ALBUM = "get-album";
    public static final String KEY_TYPE_BITS = "type-bits";
    public static final String KEY_MEDIA_TYPES = "mediaTypes";
    public static final String KEY_DISMISS_KEYGUARD = "dismiss-keyguard";
    public static final String KEY_FROM_SNAPCAM = "from-snapcam";
    public static final String KEY_TOTAL_NUMBER = "total-number";

    //add for TimelinePage and PhotoPage: -1 don't show timeline title, 0 show timeline title
    public static final int CLUSTER_ALBUMSET_NO_TITLE = -1;
    public static final int CLUSTER_ALBUMSET_TIME_TITLE = 0;

    private static final String TAG = "GalleryActivity";
    private Dialog mVersionCheckDialog;
    private ListView mDrawerListView;
    private DrawerLayout mDrawerLayout;
    public static boolean mIsparentActivityFInishing;
    NavigationDrawerListAdapter mNavigationAdapter;
    public Toolbar mToolbar;
    /** DrawerLayout is not supported in some entrances.
     * such as Intent.ACTION_VIEW, Intent.ACTION_GET_CONTENT, Intent.PICK. */
    private boolean mDrawerLayoutSupported = true;

    private static final int PERMISSION_REQUEST_STORAGE = 1;
    private Bundle mSavedInstanceState;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (getIntent().getBooleanExtra(KEY_DISMISS_KEYGUARD, false)) {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }

        setContentView(R.layout.gallery_main);
        initView();

        mSavedInstanceState = savedInstanceState;

        if (!needRequestStoragePermission()) {
            init();
        }
    }

    private void init() {
        if (mSavedInstanceState != null) {
            getStateManager().restoreFromState(mSavedInstanceState);
        } else {
            initializeByIntent();
        }

        boolean ddmBulk = SystemProperties.getBoolean("persist.gallery.dualcam.ddmbulk", false);
        if(ddmBulk)
            startBulkMpoProcess();

        mSavedInstanceState = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
            int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_STORAGE: {
                if (checkPermissionGrantResults(grantResults)) {
                    init();
                } else {
                    finish();
                }
            }
        }
    }

    private boolean needRequestStoragePermission() {
        boolean needRequest = false;
        String[] permissions = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };
        ArrayList<String> permissionList = new ArrayList<String>();
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(permission);
                needRequest = true;
            }
        }

        if (needRequest) {
            int count = permissionList.size();
            if (count > 0) {
                String[] permissionArray = new String[count];
                for (int i = 0; i < count; i++) {
                    permissionArray[i] = permissionList.get(i);
                }

                requestPermissions(permissionArray, PERMISSION_REQUEST_STORAGE);
            }
        }

        return needRequest;
    }

    private boolean checkPermissionGrantResults(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private static class ActionItem {
        public int action;
        public int title;
        public int icon;

        public ActionItem(int action, int title, int icon) {
            this.action = action;
            this.title = title;
            this.icon = icon;
        }
    }

    private static final ActionItem[] sActionItems = new ActionItem[] {
            new ActionItem(FilterUtils.CLUSTER_BY_TIME,
                    R.string.timeline_title, R.drawable.timeline),
            new ActionItem(FilterUtils.CLUSTER_BY_ALBUM, R.string.albums_title,
                    R.drawable.albums),
            new ActionItem(FilterUtils.CLUSTER_BY_VIDEOS,
                    R.string.videos_title, R.drawable.videos) };

    public void initView() {
        mDrawerListView = (ListView) findViewById(R.id.navList);
        mNavigationAdapter = new NavigationDrawerListAdapter(this);
        mDrawerListView.setAdapter(mNavigationAdapter);
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(mToolbar);

        mDrawerListView
                .setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view,
                            int position, long id) {
                        getGLRoot().lockRenderThread();
                        showScreen(position);

                        mNavigationAdapter.setClickPosition(position);
                        mDrawerListView.invalidateViews();
                        mDrawerLayout.closeDrawer(Gravity.START);
                        getGLRoot().unlockRenderThread();
                    }
                });
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawerLayout);
        mDrawerLayout.setDrawerListener(new DrawerListener() {
                @Override
                public void onDrawerStateChanged(int arg0) {
                    toggleNavDrawer(getStateManager().getStateCount() == 1);
                }

                @Override
                public void onDrawerSlide(View arg0, float arg1) {

                }

                @Override
                public void onDrawerOpened(View arg0) {

                }

                @Override
                public void onDrawerClosed(View arg0) {

                }
            });
        mToolbar.setNavigationContentDescription("drawer");
        mToolbar.setNavigationOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mToolbar.getNavigationContentDescription().equals("drawer")) {
                    mDrawerLayout.openDrawer(Gravity.START);

                } else {
                    mToolbar.setNavigationContentDescription("drawer");
                    mToolbar.setNavigationIcon(R.drawable.drawer);
                    onBackPressed();
                }
            }
        });
        setToolbar(mToolbar);
    }

    public void toggleNavDrawer(boolean setDrawerVisibility) {
        if (mDrawerLayout != null) {
            if (setDrawerVisibility && mDrawerLayoutSupported) {
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            } else {
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }
        }
    }

    public void showScreen(int position) {
        if (position > 2) {
            position = 1;
        }
        // Bundle data = new Bundle();
        // int clusterType;
        // String newPath;
        String basePath = getDataManager().getTopSetPath(
                DataManager.INCLUDE_ALL);
        switch (position) {

        case 0:
            startTimelinePage(); //Timeline view
            break;
        case 1:
            startAlbumPage(); // Albums View
            break;
        case 2:
            startVideoPage(); // Videos view
            break;
        default:
            break;
        }

        mNavigationAdapter.setClickPosition(position);

        mDrawerListView.invalidateViews();
        mToolbar.setTitle(getResources().getStringArray(
                R.array.title_array_nav_items)[position]);

        mDrawerListView.setItemChecked(position, true);
        mDrawerListView.setSelection(position);
        mToolbar.setNavigationContentDescription("drawer");
        mToolbar.setNavigationIcon(R.drawable.drawer);
    }

    private class NavigationDrawerListAdapter extends BaseAdapter {

        private int curTab = 0;
        Context mContext;

        public NavigationDrawerListAdapter(Context context) {
            mContext = context;

        }

        @Override
        public int getCount() {
            return sActionItems.length;
        }

        @Override
        public Object getItem(int position) {
            return sActionItems[position];
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) mContext
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(
                        com.android.gallery3d.R.layout.drawer_list_item, null);
            } else {
                view = convertView;
            }

            TextView titleView = (TextView) view.findViewById(R.id.itemTitle);
            ImageView iconView = (ImageView) view.findViewById(R.id.ivItem);

            titleView.setText(sActionItems[position].title);
            iconView.setImageResource(sActionItems[position].icon);

            if (curTab == position) {
                view.setBackgroundResource(R.drawable.drawer_item_selected_bg);
            } else {
                view.setBackgroundColor(android.R.color.transparent);
            }

            return view;
        }

        public void setClickPosition(int position) {
            curTab = position;
        }
    }

    public static int getActionTitle(Context context, int type) {
        for (ActionItem item : sActionItems) {
            if (item.action == type) {
                return item.title;
            }
        }
        return -1;
    }

    private void initializeByIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();

        if (Intent.ACTION_GET_CONTENT.equalsIgnoreCase(action)) {
            mDrawerLayoutSupported = false;
            startGetContent(intent);
        } else if (Intent.ACTION_PICK.equalsIgnoreCase(action)) {
            mDrawerLayoutSupported = false;
            // We do NOT really support the PICK intent. Handle it as
            // the GET_CONTENT. However, we need to translate the type
            // in the intent here.
            Log.w(TAG, "action PICK is not supported");
            String type = Utils.ensureNotNull(intent.getType());
            if (type.startsWith("vnd.android.cursor.dir/")) {
                if (type.endsWith("/image")) intent.setType("image/*");
                if (type.endsWith("/video")) intent.setType("video/*");
            }
            startGetContent(intent);
        } else if (Intent.ACTION_VIEW.equalsIgnoreCase(action)
                || ACTION_REVIEW.equalsIgnoreCase(action)){
            mDrawerLayoutSupported = false;
            startViewAction(intent);
        } else {
            mDrawerLayoutSupported = true;
            startTimelinePage();
            mToolbar.setTitle(R.string.albums_title);
        }
    }

    public void startAlbumPage() {
        PicasaSource.showSignInReminder(this);
        Bundle data = new Bundle();
        int clusterType = FilterUtils.CLUSTER_BY_ALBUM;
        data.putString(AlbumSetPage.KEY_MEDIA_PATH, getDataManager()
                .getTopSetPath(DataManager.INCLUDE_ALL));
        if (getStateManager().getStateCount() == 0)
            getStateManager().startState(AlbumSetPage.class, data);
        else {
            ActivityState state = getStateManager().getTopState();
            String oldClass = state.getClass().getSimpleName();
            String newClass = AlbumSetPage.class.getSimpleName();
            if (!oldClass.equals(newClass)) {
             getStateManager().switchState(getStateManager().getTopState(),
                    AlbumSetPage.class, data);
            }
        }
        mVersionCheckDialog = PicasaSource.getVersionCheckDialog(this);
        if (mVersionCheckDialog != null) {
            mVersionCheckDialog.setOnCancelListener(this);
        }
    }

   private void startTimelinePage() {
        String newBPath = getDataManager().getTopSetPath(DataManager.INCLUDE_ALL);
        String newPath = FilterUtils.switchClusterPath(newBPath, FilterUtils.CLUSTER_BY_TIME);
        Bundle data = new Bundle();
        data.putString(TimeLinePage.KEY_MEDIA_PATH, newPath);
        if (getStateManager().getStateCount() == 0)
            getStateManager().startState(TimeLinePage.class, data);
        else {
            ActivityState state = getStateManager().getTopState();
            String oldClass = state.getClass().getSimpleName();
            String newClass = TimeLinePage.class.getSimpleName();
            if (!oldClass.equals(newClass)) {
            getStateManager().switchState(getStateManager().getTopState(),
                    TimeLinePage.class, data);
            }
        }
        mVersionCheckDialog = PicasaSource.getVersionCheckDialog(this);
        if (mVersionCheckDialog != null) {
            mVersionCheckDialog.setOnCancelListener(this);
        }
    }

   public void startVideoPage() {
        PicasaSource.showSignInReminder(this);
        String basePath = getDataManager().getTopSetPath(
                DataManager.INCLUDE_ALL);
        Bundle data = new Bundle();
        int clusterType = FilterUtils.CLUSTER_BY_VIDEOS;
        String newPath = FilterUtils.switchClusterPath(basePath, clusterType);
        data.putString(AlbumPage.KEY_MEDIA_PATH, newPath);
        data.putBoolean(AlbumPage.KEY_IS_VIDEOS_SCREEN, true);
        ActivityState state = getStateManager().getTopState();
        String oldClass = state.getClass().getSimpleName();
        String newClass = AlbumPage.class.getSimpleName();
        if (!oldClass.equals(newClass)) {
        getStateManager().switchState(getStateManager().getTopState(),
                AlbumPage.class, data);
        }
        mVersionCheckDialog = PicasaSource.getVersionCheckDialog(this);
        if (mVersionCheckDialog != null) {
            mVersionCheckDialog.setOnCancelListener(this);
        }
    }

    private void startGetContent(Intent intent) {
        Bundle data = intent.getExtras() != null
                ? new Bundle(intent.getExtras())
                : new Bundle();
        data.putBoolean(KEY_GET_CONTENT, true);
        int typeBits = GalleryUtils.determineTypeBits(this, intent);
        data.putInt(KEY_TYPE_BITS, typeBits);
        data.putString(AlbumSetPage.KEY_MEDIA_PATH,
                getDataManager().getTopSetPath(typeBits));
        getStateManager().startState(AlbumSetPage.class, data);
    }

    private String getContentType(Intent intent) {
        String type = intent.getType();
        if (type != null) {
            return GalleryUtils.MIME_TYPE_PANORAMA360.equals(type)
                ? MediaItem.MIME_TYPE_JPEG : type;
        }

        Uri uri = intent.getData();
        try {
            return getContentResolver().getType(uri);
        } catch (Throwable t) {
            Log.w(TAG, "get type fail", t);
            return null;
        }
    }

    private void startViewAction(Intent intent) {
        Boolean slideshow = intent.getBooleanExtra(EXTRA_SLIDESHOW, false);
        if (slideshow) {
            getActionBar().hide();
            DataManager manager = getDataManager();
            Path path = manager.findPathByUri(intent.getData(), intent.getType());
            if (path == null || manager.getMediaObject(path)
                    instanceof MediaItem) {
                path = Path.fromString(
                        manager.getTopSetPath(DataManager.INCLUDE_IMAGE));
            }
            Bundle data = new Bundle();
            data.putString(SlideshowPage.KEY_SET_PATH, path.toString());
            data.putBoolean(SlideshowPage.KEY_RANDOM_ORDER, true);
            data.putBoolean(SlideshowPage.KEY_REPEAT, true);
            if (intent.getBooleanExtra(EXTRA_DREAM, false)) {
                data.putBoolean(SlideshowPage.KEY_DREAM, true);
            }
            getStateManager().startState(SlideshowPage.class, data);
        } else {
            Bundle data = new Bundle();
            DataManager dm = getDataManager();
            Uri uri = intent.getData();
            String contentType = getContentType(intent);
            if (contentType == null) {
                Toast.makeText(this,
                        R.string.no_such_item, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (uri == null) {
                int typeBits = GalleryUtils.determineTypeBits(this, intent);
                data.putInt(KEY_TYPE_BITS, typeBits);
                data.putString(AlbumSetPage.KEY_MEDIA_PATH,
                        getDataManager().getTopSetPath(typeBits));
                getStateManager().startState(AlbumSetPage.class, data);
            } else if (contentType.startsWith(
                    ContentResolver.CURSOR_DIR_BASE_TYPE)) {
                int mediaType = intent.getIntExtra(KEY_MEDIA_TYPES, 0);
                if (mediaType != 0) {
                    uri = uri.buildUpon().appendQueryParameter(
                            KEY_MEDIA_TYPES, String.valueOf(mediaType))
                            .build();
                }
                Path setPath = dm.findPathByUri(uri, null);
                MediaSet mediaSet = null;
                if (setPath != null) {
                    mediaSet = (MediaSet) dm.getMediaObject(setPath);
                }
                if (mediaSet != null) {
                    if (mediaSet.isLeafAlbum()) {
                        data.putString(AlbumPage.KEY_MEDIA_PATH, setPath.toString());
                        data.putString(AlbumPage.KEY_PARENT_MEDIA_PATH,
                                dm.getTopSetPath(DataManager.INCLUDE_ALL));
                        getStateManager().startState(AlbumPage.class, data);
                    } else {
                        data.putString(AlbumSetPage.KEY_MEDIA_PATH, setPath.toString());
                        getStateManager().startState(AlbumSetPage.class, data);
                    }
                } else {
                    startTimelinePage();
                }
            } else {
                Path itemPath = dm.findPathByUri(uri, contentType);
                Path albumPath = dm.getDefaultSetOf(itemPath);

                data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH, itemPath.toString());
                if (!intent.getBooleanExtra(KEY_FROM_SNAPCAM, false)) {
                    data.putBoolean(PhotoPage.KEY_READONLY, true);
                } else {
                    int hintIndex = 0;
                    if (View.LAYOUT_DIRECTION_RTL == TextUtils
                        .getLayoutDirectionFromLocale(Locale.getDefault())) {
                        hintIndex = intent.getIntExtra(KEY_TOTAL_NUMBER, 1) - 1;
                    }
                    data.putInt(PhotoPage.KEY_INDEX_HINT, hintIndex);
                }

                // TODO: Make the parameter "SingleItemOnly" public so other
                //       activities can reference it.
                boolean singleItemOnly = (albumPath == null)
                        || intent.getBooleanExtra("SingleItemOnly", false);
                if (!singleItemOnly) {
                    data.putString(PhotoPage.KEY_MEDIA_SET_PATH, albumPath.toString());
                }
                data.putBoolean("SingleItemOnly", singleItemOnly);
                getStateManager().startState(SinglePhotoPage.class, data);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mVersionCheckDialog != null) {
            mVersionCheckDialog.show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVersionCheckDialog != null) {
            mVersionCheckDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        cancelBulkMpoProcess();
        super.onDestroy();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        if (dialog == mVersionCheckDialog) {
            mVersionCheckDialog = null;
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        final boolean isTouchPad = (event.getSource()
                & InputDevice.SOURCE_CLASS_POSITION) != 0;
        if (isTouchPad) {
            float maxX = event.getDevice().getMotionRange(MotionEvent.AXIS_X).getMax();
            float maxY = event.getDevice().getMotionRange(MotionEvent.AXIS_Y).getMax();
            View decor = getWindow().getDecorView();
            float scaleX = decor.getWidth() / maxX;
            float scaleY = decor.getHeight() / maxY;
            float x = event.getX() * scaleX;
            //x = decor.getWidth() - x; // invert x
            float y = event.getY() * scaleY;
            //y = decor.getHeight() - y; // invert y
            MotionEvent touchEvent = MotionEvent.obtain(event.getDownTime(),
                    event.getEventTime(), event.getAction(), x, y, event.getMetaState());
            return dispatchTouchEvent(touchEvent);
        }
        return super.onGenericMotionEvent(event);
    }

    private Future<?> mMpoTask;
    private Toast mToast;
    private String mToastStr;
    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            String toastMsg = null;

            switch(msg.what) {
            case 0:
                startBulkMpoProcess();
                break;
            case 1:
                toastMsg = "MPO bulk process START";
                break;
            case 2:
                toastMsg = "MPO bulk process CANCELLED";
                break;
            case 3:
                toastMsg = "MPO bulk process DONE";
                break;
            case 4:
                toastMsg = "MPO bulk process FAILED";
                break;
            case 5:
                toastMsg = "MPO bulk processing image (" + msg.arg1 + "/" + msg.arg2 + ")";
                break;
            }

            if(toastMsg != null) {
                mToastStr = toastMsg;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(mToast == null) {
                            mToast = Toast.makeText(GalleryActivity.this, mToastStr, Toast.LENGTH_SHORT);
                        } else {
                            mToast.setText(mToastStr);
                        }
                        mToast.show();
                    }
                });
            }
            return false;
        }
    });

    private void startBulkMpoProcess() {
        try {
            mMpoTask = getBatchServiceThreadPoolIfAvailable().submit(new BatchMpoJob(this));
        } catch (Exception e) {
            mHandler.sendEmptyMessageDelayed(0, 50);
        }
    }

    private void cancelBulkMpoProcess() {
        if(mMpoTask != null) {
            mMpoTask.cancel();
            mMpoTask = null;
        }
    }

    private class BatchMpoJob implements Job<Void> {
        private Context mContext;
        private ContentResolver mContentResolver;

        public BatchMpoJob(Context context) {
            mContext = context;
            mContentResolver = mContext.getContentResolver();
        }

        @Override
        public Void run(JobContext jc) {
            mHandler.sendEmptyMessage(1);

            Cursor allPhotos = mContentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.ImageColumns._ID}, null, null, null);

            if(allPhotos != null) {
                try {
                    boolean cancelled = false;
                    int count = allPhotos.getCount();
                    while(allPhotos.moveToNext()) {
                        if (jc.isCancelled()) {
                            cancelled = true;
                            break;
                        }

                        int position = allPhotos.getPosition() + 1;
                        long id = allPhotos.getLong(0);
                        Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                        loadMpo(mContext, uri);
                        mHandler.sendMessage(mHandler.obtainMessage(5, position, count));
                    }

                    if(cancelled)
                        mHandler.sendEmptyMessage(2);
                    else
                        mHandler.sendEmptyMessage(3);
                } finally {
                    allPhotos.close();
                }
            } else {
                mHandler.sendEmptyMessage(4);
            }
            return null;
        }

        private boolean loadMpo(Context context, Uri uri) {
            boolean loaded = false;
            MpoParser parser = MpoParser.parse(context, uri);
            byte[] primaryMpoData = parser.readImgData(true);
            byte[] auxiliaryMpoData = parser.readImgData(false);

            if(primaryMpoData != null && auxiliaryMpoData != null) {
                Bitmap primaryBm = BitmapFactory.decodeByteArray(primaryMpoData, 0, primaryMpoData.length);
                primaryMpoData = null;

                if(primaryBm == null) {
                    return false;
                }

                // check for pre-generated dm file
                String mpoFilepath = ImageLoader.getLocalPathFromUri(context, uri);
                // read auxiliary image and generate depth map.
                Bitmap auxiliaryBm = BitmapFactory.decodeByteArray(auxiliaryMpoData, 0, auxiliaryMpoData.length);
                auxiliaryMpoData = null;

                if(auxiliaryBm == null) {
                    primaryBm.recycle();
                    primaryBm = null;
                    return false;
                }

                DualCameraNativeEngine.getInstance().initDepthMap(
                        primaryBm, auxiliaryBm, mpoFilepath,
                        DualCameraNativeEngine.getInstance().getCalibFilepath(context));

                primaryBm.recycle();
                primaryBm = null;
                auxiliaryBm.recycle();
                auxiliaryBm = null;
            }
            return loaded;
        }
    }
}
