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

package com.android.gallery3d.filtershow;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Vector;
import java.util.Locale;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.print.PrintHelper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.ShareActionProvider;
import android.widget.ShareActionProvider.OnShareTargetSelectedListener;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.data.LocalAlbum;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.category.Action;
import com.android.gallery3d.filtershow.category.CategoryAdapter;
import com.android.gallery3d.filtershow.category.CategorySelected;
import com.android.gallery3d.filtershow.category.CategoryView;
import com.android.gallery3d.filtershow.category.MainPanel;
import com.android.gallery3d.filtershow.category.SwipableView;
import com.android.gallery3d.filtershow.data.UserPresetsManager;
import com.android.gallery3d.filtershow.editors.Editor;
import com.android.gallery3d.filtershow.editors.EditorDualCamFusion;
import com.android.gallery3d.filtershow.editors.EditorManager;
import com.android.gallery3d.filtershow.editors.EditorPanel;
import com.android.gallery3d.filtershow.editors.ImageOnlyEditor;
import com.android.gallery3d.filtershow.editors.TrueScannerEditor;
import com.android.gallery3d.filtershow.filters.FilterDrawRepresentation;
import com.android.gallery3d.filtershow.filters.FilterMirrorRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRotateRepresentation;
import com.android.gallery3d.filtershow.filters.FilterUserPresetRepresentation;
import com.android.gallery3d.filtershow.filters.FiltersManager;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.filters.SimpleMakeupImageFilter;
import com.android.gallery3d.filtershow.filters.TrueScannerActs;
import com.android.gallery3d.filtershow.history.HistoryItem;
import com.android.gallery3d.filtershow.history.HistoryManager;
import com.android.gallery3d.filtershow.imageshow.ImageShow;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.imageshow.Spline;
import com.android.gallery3d.filtershow.info.InfoPanel;
import com.android.gallery3d.filtershow.pipeline.CachingPipeline;
import com.android.gallery3d.filtershow.pipeline.ImagePreset;
import com.android.gallery3d.filtershow.pipeline.ProcessingService;
import com.android.gallery3d.filtershow.presets.PresetManagementDialog;
import com.android.gallery3d.filtershow.presets.UserPresetsAdapter;
import com.android.gallery3d.filtershow.provider.SharedImageProvider;
import com.android.gallery3d.filtershow.state.StateAdapter;
import com.android.gallery3d.filtershow.tools.DualCameraNativeEngine;
import com.android.gallery3d.filtershow.tools.DualCameraNativeEngine.DdmStatus;
import com.android.gallery3d.filtershow.tools.SaveImage;
import com.android.gallery3d.filtershow.tools.XmpPresets;
import com.android.gallery3d.filtershow.tools.XmpPresets.XMresults;
import com.android.gallery3d.filtershow.ui.ExportDialog;
import com.android.gallery3d.filtershow.ui.FramedTextButton;
import com.android.gallery3d.mpo.MpoParser;
import com.android.gallery3d.util.GalleryUtils;
import com.android.photos.data.GalleryBitmapPool;
import com.thundersoft.hz.selfportrait.makeup.engine.MakeupEngine;

public class FilterShowActivity extends FragmentActivity implements OnItemClickListener,
        OnShareTargetSelectedListener, DialogInterface.OnShowListener,
        DialogInterface.OnDismissListener, PopupMenu.OnDismissListener{

    private String mAction = "";
    MasterImage mMasterImage = null;

    private static final long LIMIT_SUPPORTS_HIGHRES = 134217728; // 128Mb

    public static final String TINY_PLANET_ACTION = "com.android.camera.action.TINY_PLANET";
    public static final String LAUNCH_FULLSCREEN = "launch-fullscreen";
    public static final boolean RESET_TO_LOADED = false;
    private ImageShow mImageShow = null;

    private View mSaveButton = null;
    private View mDoneButton = null, mCancelButton = null;

    private EditorPlaceHolder mEditorPlaceHolder = new EditorPlaceHolder(this);
    private Editor mCurrentEditor = null;

    private static final int SELECT_PICTURE = 1;
    public static final int SELECT_FUSION_UNDERLAY = 2;
    private static final String LOGTAG = "FilterShowActivity";

    private boolean mShowingTinyPlanet = false;
    private boolean mShowingImageStatePanel = false;
    private boolean mShowingVersionsPanel = false;

    private final Vector<ImageShow> mImageViews = new Vector<ImageShow>();

    private ShareActionProvider mShareActionProvider;
    private File mSharedOutputFile = null;

    private boolean mSharingImage = false;

    private WeakReference<ProgressDialog> mSavingProgressDialog;

    private LoadBitmapTask mLoadBitmapTask;
    private LoadHighresBitmapTask mHiResBitmapTask;
    private ParseMpoDataTask mParseMpoTask;
    private LoadMpoDataTask mLoadMpoTask;

    private Uri mOriginalImageUri = null;
    private ImagePreset mOriginalPreset = null;

    private Uri mSelectedImageUri = null;

    private ArrayList<Action> mActions = new ArrayList<Action>();
    private UserPresetsManager mUserPresetsManager = null;
    private UserPresetsAdapter mUserPresetsAdapter = null;
    private CategoryAdapter mCategoryLooksAdapter = null;
    private CategoryAdapter mCategoryBordersAdapter = null;
    private CategoryAdapter mCategoryGeometryAdapter = null;
    private CategoryAdapter mCategoryFiltersAdapter = null;
    private CategoryAdapter mCategoryTrueScannerAdapter = null;
    private CategoryAdapter mCategoryHazeBusterAdapter = null;
    private CategoryAdapter mCategorySeeStraightAdapter = null;
    private CategoryAdapter mCategoryVersionsAdapter = null;
    private CategoryAdapter mCategoryMakeupAdapter = null;
    private CategoryAdapter mCategoryDualCamAdapter = null;
    private int mCurrentPanel = MainPanel.LOOKS;
    private Vector<FilterUserPresetRepresentation> mVersions =
            new Vector<FilterUserPresetRepresentation>();
    private int mVersionsCounter = 0;

    private boolean mHandlingSwipeButton = false;
    private View mHandledSwipeView = null;
    private float mHandledSwipeViewLastDelta = 0;
    private float mSwipeStartX = 0;
    private float mSwipeStartY = 0;

    private ProcessingService mBoundService;
    private boolean mIsBound = false;
    private Menu mMenu;
    private DialogInterface mCurrentDialog = null;
    private PopupMenu mCurrentMenu = null;
    private boolean mReleaseDualCamOnDestory = true;
    private ImageButton imgComparison;
    private String mPopUpText, mExit;
    RelativeLayout rlImageContainer;
    boolean isOrientationChanged;
    private boolean isComingFromEditorScreen;

    private ProgressDialog mLoadingDialog;

    public ProcessingService getProcessingService() {
        return mBoundService;
    }

    public boolean isSimpleEditAction() {
        return !PhotoPage.ACTION_NEXTGEN_EDIT.equalsIgnoreCase(mAction);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            /*
             * This is called when the connection with the service has been
             * established, giving us the service object we can use to
             * interact with the service.  Because we have bound to a explicit
             * service that we know is running in our own process, we can
             * cast its IBinder to a concrete class and directly access it.
             */
            mBoundService = ((ProcessingService.LocalBinder)service).getService();
            mBoundService.setFiltershowActivity(FilterShowActivity.this);
            mBoundService.onStart();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            /*
             * This is called when the connection with the service has been
             * unexpectedly disconnected -- that is, its process crashed.
             * Because it is running in our same process, we should never
             * see this happen.
             */
            mBoundService = null;
            MasterImage.setMaster(null);
        }
    };

    void doBindService() {
        /*
         * Establish a connection with the service.  We use an explicit
         * class name because we want a specific service implementation that
         * we know will be running in our own process (and thus won't be
         * supporting component replacement by other applications).
         */
        bindService(new Intent(FilterShowActivity.this, ProcessingService.class),
                mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    public void updateUIAfterServiceStarted() {
        MasterImage.setMaster(mMasterImage);
        ImageFilter.setActivityForMemoryToasts(this);
        mUserPresetsManager = new UserPresetsManager(this);
        mUserPresetsAdapter = new UserPresetsAdapter(this);

        setupMasterImage();
        setupMenu();
        setDefaultValues();
        getWindow().setBackgroundDrawable(new ColorDrawable(0));
        loadXML();

        fillCategories();
        loadMainPanel();
        extractXMPData();
        processIntent();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean onlyUsePortrait = getResources().getBoolean(R.bool.only_use_portrait);
        if (onlyUsePortrait) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        clearGalleryBitmapPool();
        doBindService();
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.GRAY));
        setContentView(R.layout.filtershow_splashscreen);
        Window win = getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        winParams.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        win.setAttributes(winParams);
    }

    public boolean isShowingImageStatePanel() {
        return mShowingImageStatePanel;
    }

    public void loadMainPanel() {
        if (findViewById(R.id.main_panel_container) == null) {
            return;
        }
        MainPanel panel = new MainPanel();
        Bundle bundle = new Bundle();
        bundle.putBoolean(MainPanel.EDITOR_TAG, isComingFromEditorScreen);
        panel.setArguments(bundle);
        isComingFromEditorScreen = false;
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.main_panel_container, panel, MainPanel.FRAGMENT_TAG);
        transaction.commitAllowingStateLoss();
    }

    public void loadEditorPanel(FilterRepresentation representation,
                                final Editor currentEditor) {
        if (representation.getEditorId() == ImageOnlyEditor.ID) {
            currentEditor.reflectCurrentFilter();
            return;
        }
        final int currentId = currentEditor.getID();
        Runnable showEditor = new Runnable() {
            @Override
            public void run() {
                EditorPanel panel = new EditorPanel();
                panel.setEditor(currentId);
                setActionBarForEffects(currentEditor);
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.remove(getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG));
                transaction.replace(R.id.main_panel_container, panel, MainPanel.FRAGMENT_TAG);
                transaction.commit();
            }
        };
        Fragment main = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
        boolean doAnimation = false;
        if (mShowingImageStatePanel
                && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            doAnimation = true;
        }
        if (doAnimation && main != null && main instanceof MainPanel) {
            MainPanel mainPanel = (MainPanel) main;
            View container = mainPanel.getView().findViewById(R.id.category_panel_container);
            View bottom = mainPanel.getView().findViewById(R.id.bottom_panel);
            int panelHeight = container.getHeight() + bottom.getHeight();
            ViewPropertyAnimator anim = mainPanel.getView().animate();
            anim.translationY(panelHeight).start();
            final Handler handler = new Handler();
            handler.postDelayed(showEditor, anim.getDuration());
        } else {
            showEditor.run();
        }
    }

    public void toggleInformationPanel() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);

        InfoPanel panel = new InfoPanel();
        panel.show(transaction, InfoPanel.FRAGMENT_TAG);
    }

    private void loadXML() {
        setContentView(R.layout.filtershow_activity);
        Resources r = getResources();
        setActionBar(false);
        mPopUpText = r.getString(R.string.discard).toUpperCase(
                Locale.getDefault());
        mExit = r.getString(R.string.exit).toUpperCase(Locale.getDefault());
        int marginTop = r.getDimensionPixelSize(R.dimen.compare_margin_top);
        int marginRight = r.getDimensionPixelSize(R.dimen.compare_margin_right);
        imgComparison = (ImageButton) findViewById(R.id.imgComparison);
        rlImageContainer = (RelativeLayout) findViewById(R.id.imageContainer);

        mImageShow = (ImageShow) findViewById(R.id.imageShow);
        mImageViews.add(mImageShow);

        setupEditors();

        mEditorPlaceHolder.hide();
        mImageShow.attach();

        setupStatePanel();

        imgComparison.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                action = action & MotionEvent.ACTION_MASK;
                if (action == MotionEvent.ACTION_DOWN) {

                    HistoryManager adapter = mMasterImage.getHistory();
                    int position = adapter.backToOriginal();// adapter.undo();
                    mMasterImage.onHistoryItemClick(position);
                    v.setPressed(true);
                    backToMain();
                    invalidateViews();
                }
                if (action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL
                        || action == MotionEvent.ACTION_OUTSIDE) {
                    v.setPressed(false);
                    HistoryManager adapter = mMasterImage.getHistory();
                    int position = adapter.backToCurrent();
                    mMasterImage.onHistoryItemClick(position);
                    invalidateViews();

                }

                return false;
            }
        });
    }

    public void toggleComparisonButtonVisibility() {
            if (imgComparison.getVisibility() == View.VISIBLE)
                imgComparison.setVisibility(View.GONE);
    }

    public void setActionBar(boolean isEffectClicked) {
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setBackgroundDrawable(new ColorDrawable(getResources()
                .getColor(R.color.edit_actionbar_background)));
        if (!isEffectClicked) {
            actionBar.setCustomView(R.layout.filtershow_actionbar);
            mSaveButton = actionBar.getCustomView();
            mSaveButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    saveImage();
                }
            });
            invalidateOptionsMenu();
        } else {
            if (mMenu != null) {
                mMenu.clear();
            }
            actionBar.setCustomView(R.layout.filtershow_actionbar_new);
            mCancelButton = actionBar.getCustomView().findViewById(
                    R.id.imgCancel);
            mDoneButton = actionBar.getCustomView().findViewById(R.id.imgDone);
            mCancelButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    setActionBar(false);
                    HistoryManager adapter = mMasterImage.getHistory();
                    int position = adapter.undoCurrentFilter();
                     mMasterImage.onHistoryItemClick(position);
                     adapter.resetActiveFilter();
                    backToMain();
                    invalidateViews();
                }
            });
            mDoneButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    // saveImage();
                    setActionBar(false);
                    HistoryManager adapter = mMasterImage
                            .getHistory();
                    adapter.resetActiveFilter();
                }
            });
        }

    }

    public void setActionBarForEffects(final Editor currentEditor) {
        View view;
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        view = inflater.inflate(R.layout.filtershow_editor_panel, null);
        View editControl = view.findViewById(R.id.controlArea);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setBackgroundDrawable(new ColorDrawable(getResources()
                .getColor(R.color.edit_actionbar_background)));
        actionBar.setCustomView(R.layout.filtershow_actionbar_effects);
        ImageButton cancelButton = (ImageButton) actionBar.getCustomView()
                .findViewById(R.id.cancelFilter);
        ImageButton applyButton = (ImageButton) actionBar.getCustomView()
                .findViewById(R.id.applyFilter);
        Button editTitle = (Button) actionBar.getCustomView().findViewById(
                R.id.applyEffect);
        editTitle.setTransformationMethod(null);
        View actionControl = actionBar.getCustomView().findViewById(
                R.id.panelAccessoryViewList);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelCurrentFilter();
                FilterShowActivity.this.backToMain();
                setActionBar(false);
            }
        });
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentEditor.finalApplyCalled();
                FilterShowActivity.this.backToMain();
                setActionBar(false);
            }
        });

        if (currentEditor != null) {
            currentEditor.setUpEditorUI(editTitle, actionControl, editControl);
            currentEditor.reflectCurrentFilter();
            if (currentEditor.useUtilityPanel()) {
                currentEditor.openUtilityPanel((LinearLayout) actionControl);
            }
        }
    }

    public void cancelCurrentFilter() {
        MasterImage masterImage = MasterImage.getImage();
        HistoryManager adapter = masterImage.getHistory();

        int position = adapter.undo();
        masterImage.onHistoryItemClick(position);
        invalidateViews();
    }

    public void adjustCompareButton(boolean scaled) {
        Resources r = getResources();
        int marginTop, marginRight;
        if (scaled) {
            marginTop = r
                    .getDimensionPixelSize(R.dimen.compare_margin_top_scaled);
            marginRight = r
                    .getDimensionPixelSize(R.dimen.compare_margin_right_scaled);
        } else {
            marginTop = r.getDimensionPixelSize(R.dimen.compare_margin_top);
            marginRight = r.getDimensionPixelSize(R.dimen.compare_margin_right);

        }
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) imgComparison
                .getLayoutParams();
        params.setMargins(0, marginTop, marginRight, 0);
        imgComparison.setLayoutParams(params);
    }

    public void fillCategories() {
        fillLooks();
        loadUserPresets();
        fillBorders();
        fillTools();
        fillEffects();
        fillTrueScanner();
        fillHazeBuster();
        fillSeeStraight();
        fillVersions();
        fillMakeup();
        fillDualCamera();
    }

    public void setupStatePanel() {
        MasterImage.getImage().setHistoryManager(mMasterImage.getHistory());
    }

    private void fillVersions() {
        if (mCategoryVersionsAdapter != null) {
            mCategoryVersionsAdapter.clear();
        }
        mCategoryVersionsAdapter = new CategoryAdapter(this);
        mCategoryVersionsAdapter.setShowAddButton(true);
    }

    public void registerAction(Action action) {
        if (mActions.contains(action)) {
            return;
        }
        mActions.add(action);
    }

    private void loadActions() {
        for (int i = 0; i < mActions.size(); i++) {
            Action action = mActions.get(i);
            action.setImageFrame(new Rect(0, 0, 96, 96), 0);
        }
    }

    public void updateVersions() {
        mCategoryVersionsAdapter.clear();
        FilterUserPresetRepresentation originalRep = new FilterUserPresetRepresentation(
                getString(R.string.filtershow_version_original), new ImagePreset(), -1);
        mCategoryVersionsAdapter.add(
                new Action(this, originalRep, Action.FULL_VIEW));
        ImagePreset current = new ImagePreset(MasterImage.getImage().getPreset());
        FilterUserPresetRepresentation currentRep = new FilterUserPresetRepresentation(
                getString(R.string.filtershow_version_current), current, -1);
        mCategoryVersionsAdapter.add(
                new Action(this, currentRep, Action.FULL_VIEW));
        if (mVersions.size() > 0) {
            mCategoryVersionsAdapter.add(new Action(this, Action.SPACER));
        }
        for (FilterUserPresetRepresentation rep : mVersions) {
            mCategoryVersionsAdapter.add(
                    new Action(this, rep, Action.FULL_VIEW, true));
        }
        mCategoryVersionsAdapter.notifyDataSetInvalidated();
    }

    public void addCurrentVersion() {
        ImagePreset current = new ImagePreset(MasterImage.getImage().getPreset());
        mVersionsCounter++;
        FilterUserPresetRepresentation rep = new FilterUserPresetRepresentation(
                "" + mVersionsCounter, current, -1);
        mVersions.add(rep);
        updateVersions();
    }

    public void removeVersion(Action action) {
        mVersions.remove(action.getRepresentation());
        updateVersions();
    }

    public void removeLook(Action action) {
        FilterUserPresetRepresentation rep =
                (FilterUserPresetRepresentation) action.getRepresentation();
        if (rep == null) {
            return;
        }
        mUserPresetsManager.delete(rep.getId());
        updateUserPresetsFromManager();
    }

    private void fillEffects() {
        FiltersManager filtersManager = FiltersManager.getManager();
        ArrayList<FilterRepresentation> filtersRepresentations = filtersManager.getEffects();
        if (mCategoryFiltersAdapter != null) {
            mCategoryFiltersAdapter.clear();
        }
        mCategoryFiltersAdapter = new CategoryAdapter(this);
        for (FilterRepresentation representation : filtersRepresentations) {
            if (representation.getTextId() != 0) {
                representation.setName(getString(representation.getTextId()));
            }
            mCategoryFiltersAdapter.add(new Action(this, representation));
        }
    }

    private void fillMakeup() {
        if(!SimpleMakeupImageFilter.HAS_TS_MAKEUP) {
            return;
        }

        FiltersManager filtersManager = FiltersManager.getManager();
        ArrayList<FilterRepresentation> makeups = filtersManager.getMakeup();
        if (mCategoryMakeupAdapter != null) {
            mCategoryMakeupAdapter.clear();
        }
        mCategoryMakeupAdapter = new CategoryAdapter(this);
        for (FilterRepresentation makeup : makeups) {
            if (makeup.getTextId() != 0) {
                makeup.setName(getString(makeup.getTextId()));
            }
            mCategoryMakeupAdapter.add(new Action(this, makeup));
        }
    }

    private void fillDualCamera() {
        FiltersManager filtersManager = FiltersManager.getManager();
        ArrayList<FilterRepresentation> filtersRepresentations = filtersManager.getDualCamera();
        if (mCategoryDualCamAdapter != null) {
            mCategoryDualCamAdapter.clear();
        }
        mCategoryDualCamAdapter = new CategoryAdapter(this);
        for (FilterRepresentation representation : filtersRepresentations) {
            if (representation.getTextId() != 0) {
                representation.setName(getString(representation.getTextId()));
            }
            mCategoryDualCamAdapter.add(new Action(this, representation));
        }
    }

    private void fillTrueScanner() {
        FiltersManager filtersManager = FiltersManager.getManager();
        ArrayList<FilterRepresentation> trueScannerRepresentations = filtersManager.getTrueScanner();
        if (mCategoryTrueScannerAdapter != null) {
            mCategoryTrueScannerAdapter.clear();
        }
        mCategoryTrueScannerAdapter = new CategoryAdapter(this);
        for (FilterRepresentation representation : trueScannerRepresentations) {
            if (representation.getTextId() != 0) {
                representation.setName(getString(representation.getTextId()));
            }
            mCategoryTrueScannerAdapter.add(new Action(this, representation));
        }
    }

    private void fillHazeBuster() {
        FiltersManager filtersManager = FiltersManager.getManager();
        ArrayList<FilterRepresentation> hazeBusterRepresentations = filtersManager.getHazeBuster();
        if (mCategoryHazeBusterAdapter != null) {
            mCategoryHazeBusterAdapter.clear();
        }
        mCategoryHazeBusterAdapter = new CategoryAdapter(this);
        for (FilterRepresentation representation : hazeBusterRepresentations) {
            if (representation.getTextId() != 0) {
                representation.setName(getString(representation.getTextId()));
            }
            mCategoryHazeBusterAdapter.add(new Action(this, representation));
        }
    }

    private void fillSeeStraight() {
        FiltersManager filtersManager = FiltersManager.getManager();
        ArrayList<FilterRepresentation> hazeBusterRepresentations = filtersManager.getSeeStraight();
        if (mCategorySeeStraightAdapter != null) {
            mCategorySeeStraightAdapter.clear();
        }
        mCategorySeeStraightAdapter = new CategoryAdapter(this);
        for (FilterRepresentation representation : hazeBusterRepresentations) {
            if (representation.getTextId() != 0) {
                representation.setName(getString(representation.getTextId()));
            }
            mCategorySeeStraightAdapter.add(new Action(this, representation));
        }
    }

    private void fillTools() {
        FiltersManager filtersManager = FiltersManager.getManager();
        ArrayList<FilterRepresentation> filtersRepresentations = filtersManager.getTools();
        if (mCategoryGeometryAdapter != null) {
            mCategoryGeometryAdapter.clear();
        }
        mCategoryGeometryAdapter = new CategoryAdapter(this);
        boolean found = false;
        for (FilterRepresentation representation : filtersRepresentations) {
            mCategoryGeometryAdapter.add(new Action(this, representation));
            if (representation instanceof FilterDrawRepresentation) {
                found = true;
            }
        }
        if (!found) {
            FilterRepresentation representation =
                    new FilterDrawRepresentation(getString(R.string.imageDraw));
            Action action = new Action(this, representation);
            action.setIsDoubleAction(true);
            mCategoryGeometryAdapter.add(action);
        }
    }

    private void processIntent() {
        Intent intent = getIntent();
        if (intent.getBooleanExtra(LAUNCH_FULLSCREEN, false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        mAction = intent.getAction();
        mSelectedImageUri = intent.getData();
        Uri loadUri = mSelectedImageUri;
        if (mOriginalImageUri != null) {
            loadUri = mOriginalImageUri;
        }
        if (loadUri != null) {
            startLoadBitmap(loadUri);
        } else {
            pickImage(SELECT_PICTURE);
        }
    }

    private void setupEditors() {
        FrameLayout editorContainer = (FrameLayout) findViewById(R.id.editorContainer);
        LayoutParams layoutParams = (LayoutParams) editorContainer.getLayoutParams();
        // if topMargin is not set in xml, set a default value here.
        if (layoutParams.topMargin == 0) {
            layoutParams.topMargin = getActionBarHeight() + getStatusBarHeight();
            editorContainer.setLayoutParams(layoutParams);
        }

        mEditorPlaceHolder.setContainer(editorContainer);
        EditorManager.addEditors(mEditorPlaceHolder);
        mEditorPlaceHolder.setOldViews(mImageViews);
    }

    private int getActionBarHeight() {
        ActionBar actionBar = getActionBar();
        return actionBar == null ? 0 : actionBar.getHeight();
    }

    private int getStatusBarHeight() {
        Rect frame = new Rect();
        getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
        return frame.top;
    }

    private void setDefaultValues() {
        Resources res = getResources();

        // TODO: get those values from XML.
        FramedTextButton.setTextSize((int) getPixelsFromDip(14));
        FramedTextButton.setTrianglePadding((int) getPixelsFromDip(4));
        FramedTextButton.setTriangleSize((int) getPixelsFromDip(10));

        Drawable curveHandle = res.getDrawable(R.drawable.camera_crop);
        int curveHandleSize = (int) res.getDimension(R.dimen.crop_indicator_size);
        Spline.setCurveHandle(curveHandle, curveHandleSize);
        Spline.setCurveWidth((int) getPixelsFromDip(3));

        mOriginalImageUri = null;
    }

    private void startLoadBitmap(Uri uri) {
        final View imageShow = findViewById(R.id.imageShow);
        imageShow.setVisibility(View.INVISIBLE);
        startLoadingIndicator();
        mShowingTinyPlanet = false;
        mLoadBitmapTask = new LoadBitmapTask();
        mLoadBitmapTask.execute(uri);

        if(DualCameraNativeEngine.getInstance().isLibLoaded()) {
            mParseMpoTask = new ParseMpoDataTask();
            mParseMpoTask.execute();
        } else {
            MasterImage.getImage().setDepthMapLoadingStatus(DdmStatus.DDM_FAILED);
        }
    }

    private void fillBorders() {
        FiltersManager filtersManager = FiltersManager.getManager();
        ArrayList<FilterRepresentation> borders = filtersManager.getBorders();
        mCategoryBordersAdapter = new CategoryAdapter(this);

        for (int i = 0; i < borders.size(); i++) {
            FilterRepresentation filter = borders.get(i);
            filter.setName(getString(R.string.borders) + "" + i);
            if (i == 0) {
                filter.setName(getString(R.string.none));
            }
            mCategoryBordersAdapter.add(new Action(this, filter, Action.FULL_VIEW));
        }
    }

    public UserPresetsAdapter getUserPresetsAdapter() {
        return mUserPresetsAdapter;
    }

    public CategoryAdapter getCategoryLooksAdapter() {
        return mCategoryLooksAdapter;
    }

    public CategoryAdapter getCategoryBordersAdapter() {
        return mCategoryBordersAdapter;
    }

    public CategoryAdapter getCategoryMakeupAdapter() {
        return mCategoryMakeupAdapter;
    }

    public CategoryAdapter getCategoryGeometryAdapter() {
        return mCategoryGeometryAdapter;
    }

    public CategoryAdapter getCategoryFiltersAdapter() {
        return mCategoryFiltersAdapter;
    }

    public CategoryAdapter getCategoryTrueScannerAdapter() {
        return mCategoryTrueScannerAdapter;
    }

    public CategoryAdapter getCategoryHazeBusterAdapter() {
        return mCategoryHazeBusterAdapter;
    }

    public CategoryAdapter getCategorySeeStraightAdapter() {
        return mCategorySeeStraightAdapter;
    }

    public CategoryAdapter getCategoryVersionsAdapter() {
        return mCategoryVersionsAdapter;
    }

    public CategoryAdapter getCategoryDualCamAdapter() {
        return mCategoryDualCamAdapter;
    }

    public void removeFilterRepresentation(FilterRepresentation filterRepresentation) {
        if (filterRepresentation == null) {
            return;
        }
        ImagePreset oldPreset = MasterImage.getImage().getPreset();
        ImagePreset copy = new ImagePreset(oldPreset);
        copy.removeFilter(filterRepresentation);
        MasterImage.getImage().setPreset(copy, copy.getLastRepresentation(), true);
        if (MasterImage.getImage().getCurrentFilterRepresentation() == filterRepresentation) {
            FilterRepresentation lastRepresentation = copy.getLastRepresentation();
            MasterImage.getImage().setCurrentFilterRepresentation(lastRepresentation);
        }
    }

    public void useFilterRepresentation(FilterRepresentation filterRepresentation) {
        if (filterRepresentation == null) {
            return;
        }
        if (!(filterRepresentation instanceof FilterRotateRepresentation)
            && !(filterRepresentation instanceof FilterMirrorRepresentation)
            && MasterImage.getImage().getCurrentFilterRepresentation() == filterRepresentation) {
            return;
        }
        if (filterRepresentation instanceof FilterUserPresetRepresentation
                || filterRepresentation instanceof FilterRotateRepresentation
                || filterRepresentation instanceof FilterMirrorRepresentation) {
            MasterImage.getImage().onNewLook(filterRepresentation);
        }
        ImagePreset oldPreset = MasterImage.getImage().getPreset();
        ImagePreset copy = new ImagePreset(oldPreset);
        FilterRepresentation representation = copy.getRepresentation(filterRepresentation);
        if (representation == null) {
            filterRepresentation = filterRepresentation.copy();
            copy.addFilter(filterRepresentation);
        } else {
            if (filterRepresentation.allowsSingleInstanceOnly()) {
                // Don't just update the filter representation. Centralize the
                // logic in the addFilter(), such that we can keep "None" as
                // null.
                if (!representation.equals(filterRepresentation)) {
                    // Only do this if the filter isn't the same
                    // (state panel clicks can lead us here)
                    copy.removeFilter(representation);
                    copy.addFilter(filterRepresentation);
                }
            }
        }
        MasterImage.getImage().setPreset(copy, filterRepresentation, true);
        MasterImage.getImage().setCurrentFilterRepresentation(filterRepresentation);
    }

    public void showRepresentation(FilterRepresentation representation) {
        if (representation == null) {
            return;
        }

        if (representation.getFilterType() == FilterRepresentation.TYPE_DUALCAM &&
                MasterImage.getImage().getDepthMapLoadingStatus() == DdmStatus.DDM_FAILED) {
            Toast.makeText(this, getString(R.string.dualcam_filter_not_supported), Toast.LENGTH_SHORT).show();
            return;
        }
        if (representation instanceof FilterRotateRepresentation) {
            FilterRotateRepresentation r = (FilterRotateRepresentation) representation;
            r.rotateCW();
        }
        if (representation instanceof FilterMirrorRepresentation) {
            FilterMirrorRepresentation r = (FilterMirrorRepresentation) representation;
            r.cycle();
        }
        if (representation.isBooleanFilter()) {
            ImagePreset preset = MasterImage.getImage().getPreset();
            if (preset.getRepresentation(representation) != null) {
                // remove
                ImagePreset copy = new ImagePreset(preset);
                copy.removeFilter(representation);
                FilterRepresentation filterRepresentation = representation.copy();
                MasterImage.getImage().setPreset(copy, filterRepresentation, true);
                MasterImage.getImage().setCurrentFilterRepresentation(null);
                return;
            }
        }
        useFilterRepresentation(representation);

        // show representation
        if (mCurrentEditor != null) {
            mCurrentEditor.detach();
        }
        mCurrentEditor = mEditorPlaceHolder.showEditor(representation.getEditorId());
        loadEditorPanel(representation, mCurrentEditor);
    }

    public Editor getEditor(int editorID) {
        return mEditorPlaceHolder.getEditor(editorID);
    }

    public void setCurrentPanel(int currentPanel) {
        mCurrentPanel = currentPanel;
        HistoryManager adapter = mMasterImage.getHistory();
        adapter.setActiveFilter(currentPanel);
    }

    public int getCurrentPanel() {
        return mCurrentPanel;
    }

    public void updateCategories() {
        if (mMasterImage == null) {
            return;
        }
        ImagePreset preset = mMasterImage.getPreset();
        mCategoryLooksAdapter.reflectImagePreset(preset);
        mCategoryBordersAdapter.reflectImagePreset(preset);
        mCategoryDualCamAdapter.reflectImagePreset(preset);
    }

    public View getMainStatePanelContainer(int id) {
        return findViewById(id);
    }

    public void onShowMenu(PopupMenu menu) {
        mCurrentMenu = menu;
        menu.setOnDismissListener(this);
    }

    @Override
    public void onDismiss(PopupMenu popupMenu){
        if (mCurrentMenu == null) {
            return;
        }
        mCurrentMenu.setOnDismissListener(null);
        mCurrentMenu = null;
    }

    @Override
    public void onShow(DialogInterface dialog) {
        mCurrentDialog = dialog;
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        mCurrentDialog = null;
    }

    private class LoadHighresBitmapTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            MasterImage master = MasterImage.getImage();
            Rect originalBounds = master.getOriginalBounds();
            if (master.supportsHighRes()) {
                int highresPreviewSize = Math.min(MasterImage.MAX_BITMAP_DIM, getScreenImageSize());
                Log.d(LOGTAG, "FilterShowActivity.LoadHighresBitmapTask.doInBackground(): after, highresPreviewSize is " + highresPreviewSize);
                Rect bounds = new Rect();
                Bitmap originalHires = ImageLoader.loadOrientedConstrainedBitmap(master.getUri(),
                        master.getActivity(), highresPreviewSize,
                        master.getOrientation(), bounds);
                master.setOriginalBounds(bounds);
                master.setOriginalBitmapHighres(originalHires);
                Log.d(LOGTAG, "FilterShowActivity.LoadHighresBitmapTask.doInBackground(): originalHires.WH is (" + originalHires.getWidth()
                        + ", " + originalHires.getHeight() +"), bounds is " + bounds.toString());
                mBoundService.setOriginalBitmapHighres(originalHires);
                master.warnListeners();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Bitmap highresBitmap = MasterImage.getImage().getOriginalBitmapHighres();
            if (highresBitmap != null) {
                float highResPreviewScale = (float) highresBitmap.getWidth()
                        / (float) MasterImage.getImage().getOriginalBounds().width();
                Log.d(LOGTAG, "FilterShowActivity.LoadHighresBitmapTask.onPostExecute(): highResPreviewScale is " + highResPreviewScale);
                mBoundService.setHighresPreviewScaleFactor(highResPreviewScale);
            }

            MasterImage.getImage().warnListeners();
        }
    }

    private class ParseMpoDataTask extends AsyncTask<Void, Void, byte[]> {
        @Override
        protected void onPreExecute() {
            MasterImage.getImage().setDepthMapLoadingStatus(DdmStatus.DDM_PARSING);
        }

        @Override
        protected byte[] doInBackground(Void... params) {
            MpoParser parser = MpoParser.parse(FilterShowActivity.this, MasterImage.getImage().getUri());
            return parser.readImgData(false);
        }

        @Override
        protected void onPostExecute(byte[] result) {
            if(result == null) {
                // parse failed
                MasterImage.getImage().setDepthMapLoadingStatus(DdmStatus.DDM_FAILED);
            } else {
                mLoadMpoTask = new LoadMpoDataTask();
                mLoadMpoTask.execute(result);
            }

            Fragment currentPanel = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
            if (currentPanel instanceof MainPanel) {
                MainPanel mainPanel = (MainPanel) currentPanel;
                mainPanel.updateDualCameraButton();
            }
        }
    }

    private class LoadMpoDataTask extends AsyncTask<byte[], Void, Boolean> {
        @Override
        protected void onPreExecute() {
            MasterImage.getImage().setDepthMapLoadingStatus(DdmStatus.DDM_LOADING);
        }

        @Override
        protected Boolean doInBackground(byte[]... params) {
            return MasterImage.getImage().loadMpo(params[0]);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            MasterImage.getImage().setDepthMapLoadingStatus(result?DdmStatus.DDM_LOADED:DdmStatus.DDM_FAILED);
            stopLoadingIndicator();
        }
    }

    public boolean isLoadingVisible() {
        if(mLoadingDialog != null) {
            return mLoadingDialog.isShowing();
        }

        return false;
    }

    public void startLoadingIndicator() {
        if(mLoadingDialog == null) {
            mLoadingDialog = new ProgressDialog(this);
            mLoadingDialog.setMessage(getString(R.string.loading_image));
            mLoadingDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mLoadingDialog.setIndeterminate(true);
            mLoadingDialog.setCancelable(true);
            mLoadingDialog.setCanceledOnTouchOutside(false);
            mLoadingDialog.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    done();
                }
            });
        }

        mLoadingDialog.show();
    }

    public void stopLoadingIndicator() {
        if(mLoadingDialog != null && mLoadingDialog.isShowing()) {
            mLoadingDialog.dismiss();
        }
     }

    private class LoadBitmapTask extends AsyncTask<Uri, Boolean, Boolean> {
        int mBitmapSize;

        public LoadBitmapTask() {
            mBitmapSize = getScreenImageSize();
            Log.d(LOGTAG, "FilterShowActivity.LoadBitmapTask(): mBitmapSize is " + mBitmapSize);
        }

        @Override
        protected Boolean doInBackground(Uri... params) {
            if (!MasterImage.getImage().loadBitmap(params[0], mBitmapSize)) {
                return false;
            }
            publishProgress(ImageLoader.queryLightCycle360(MasterImage.getImage().getActivity()));
            return true;
        }

        @Override
        protected void onProgressUpdate(Boolean... values) {
            super.onProgressUpdate(values);
            if (isCancelled()) {
                return;
            }
            if (values[0]) {
                mShowingTinyPlanet = true;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            MasterImage.setMaster(mMasterImage);
            if (isCancelled()) {
                return;
            }

            if (!result) {
                if (mOriginalImageUri != null
                        && !mOriginalImageUri.equals(mSelectedImageUri)) {
                    mOriginalImageUri = mSelectedImageUri;
                    mOriginalPreset = null;
                    Toast.makeText(FilterShowActivity.this,
                            R.string.cannot_edit_original, Toast.LENGTH_SHORT).show();
                    startLoadBitmap(mOriginalImageUri);
                } else {
                    cannotLoadImage();
                }
                return;
            }

            if (null == CachingPipeline.getRenderScriptContext()) {
                Log.v(LOGTAG, "RenderScript context destroyed during load");
                return;
            }
            final View imageShow = findViewById(R.id.imageShow);
            imageShow.setVisibility(View.VISIBLE);


            Bitmap largeBitmap = MasterImage.getImage().getOriginalBitmapLarge();
            mBoundService.setOriginalBitmap(largeBitmap);

            float previewScale = (float) largeBitmap.getWidth()
                    / (float) MasterImage.getImage().getOriginalBounds().width();
            Log.d(LOGTAG, "FilterShowActivity.LoadBitmapTask.onPostExecute(): previewScale is " + previewScale);
            mBoundService.setPreviewScaleFactor(previewScale);
            if (!mShowingTinyPlanet) {
                mCategoryFiltersAdapter.removeTinyPlanet();
            }
            mCategoryLooksAdapter.imageLoaded();
            mCategoryBordersAdapter.imageLoaded();
            mCategoryGeometryAdapter.imageLoaded();
            mCategoryFiltersAdapter.imageLoaded();
            mCategoryDualCamAdapter.imageLoaded();
            if(mCategoryMakeupAdapter != null) {
                mCategoryMakeupAdapter.imageLoaded();
            }
            mLoadBitmapTask = null;

            MasterImage.getImage().warnListeners();
            loadActions();

            if (mOriginalPreset != null) {
                MasterImage.getImage().setLoadedPreset(mOriginalPreset);
                MasterImage.getImage().setPreset(mOriginalPreset,
                        mOriginalPreset.getLastRepresentation(), true);
                mOriginalPreset = null;
            } else {
                setDefaultPreset();
            }

            MasterImage.getImage().resetGeometryImages(true);

            if (mAction.equals(TINY_PLANET_ACTION)) {
                showRepresentation(mCategoryFiltersAdapter.getTinyPlanet());
            }
            mHiResBitmapTask = new LoadHighresBitmapTask();
            mHiResBitmapTask.execute();
            MasterImage.getImage().warnListeners();
            super.onPostExecute(result);
        }

    }

    private void clearGalleryBitmapPool() {
        (new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                // Free memory held in Gallery's Bitmap pool.  May be O(n) for n bitmaps.
                GalleryBitmapPool.getInstance().clear();
                return null;
            }
        }).execute();
    }

    @Override
    protected void onDestroy() {
        if (mLoadBitmapTask != null) {
            mLoadBitmapTask.cancel(false);
        }

        if(mHiResBitmapTask != null) {
            mHiResBitmapTask.cancel(false);
        }

        if(mParseMpoTask != null) {
            mParseMpoTask.cancel(false);
        }

        if(mLoadMpoTask != null) {
            mLoadMpoTask.cancel(false);
        }

        mUserPresetsManager.close();
        doUnbindService();
        if (mReleaseDualCamOnDestory && DualCameraNativeEngine.getInstance().isLibLoaded())
            DualCameraNativeEngine.getInstance().releaseDepthMap();
        super.onDestroy();
    }

    // TODO: find a more robust way of handling image size selection
    // for high screen densities.
    private int getScreenImageSize() {
        DisplayMetrics outMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(outMetrics);
        return Math.max(outMetrics.heightPixels, outMetrics.widthPixels);
    }

    private void showSavingProgress(String albumName) {
        ProgressDialog progress;
        if (mSavingProgressDialog != null) {
            progress = mSavingProgressDialog.get();
            if (progress != null) {
                progress.show();
                return;
            }
        }
        // TODO: Allow cancellation of the saving process
        String progressText;
        if (albumName == null) {
            progressText = getString(R.string.saving_image);
        } else {
            progressText = getString(R.string.filtershow_saving_image, albumName);
        }
        progress = ProgressDialog.show(this, "", progressText, true, false);
        mSavingProgressDialog = new WeakReference<ProgressDialog>(progress);
    }

    private void hideSavingProgress() {
        if (mSavingProgressDialog != null) {
            ProgressDialog progress = mSavingProgressDialog.get();
            if (progress != null)
                progress.dismiss();
        }
    }

    public void completeSaveImage(Uri saveUri, boolean releaseDualCam) {
        if (mSharingImage && mSharedOutputFile != null) {
            // Image saved, we unblock the content provider
            Uri uri = Uri.withAppendedPath(SharedImageProvider.CONTENT_URI,
                    Uri.encode(mSharedOutputFile.getAbsolutePath()));
            ContentValues values = new ContentValues();
            values.put(SharedImageProvider.PREPARE, false);
            getContentResolver().insert(uri, values);
        }
        setResult(RESULT_OK, new Intent().setData(saveUri));
        if (releaseDualCam && DualCameraNativeEngine.getInstance().isLibLoaded())
            DualCameraNativeEngine.getInstance().releaseDepthMap();
        hideSavingProgress();
        finish();
    }

    @Override
    public boolean onShareTargetSelected(ShareActionProvider arg0, Intent arg1) {
        // First, let's tell the SharedImageProvider that it will need to wait
        // for the image
        Uri uri = Uri.withAppendedPath(SharedImageProvider.CONTENT_URI,
                Uri.encode(mSharedOutputFile.getAbsolutePath()));
        ContentValues values = new ContentValues();
        values.put(SharedImageProvider.PREPARE, true);
        getContentResolver().insert(uri, values);
        mSharingImage = true;

        // Process and save the image in the background.
        showSavingProgress(null);
        mImageShow.saveImage(this, mSharedOutputFile);
        return true;
    }

    private Intent getDefaultShareIntent() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType(SharedImageProvider.MIME_TYPE);
        mSharedOutputFile = SaveImage.getNewFile(this, MasterImage.getImage().getUri());
        Uri uri = Uri.withAppendedPath(SharedImageProvider.CONTENT_URI,
                Uri.encode(mSharedOutputFile.getAbsolutePath()));
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        return intent;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        getMenuInflater().inflate(R.menu.filtershow_activity_menu, menu);
        mShareActionProvider = (ShareActionProvider) menu.findItem(
                                R.id.menu_share).getActionProvider();
        mShareActionProvider.setShareIntent(getDefaultShareIntent());
        mShareActionProvider.setOnShareTargetSelectedListener(this);
        mMenu = menu;
        setupMenu();
        return true;
    }

    private void setupMenu(){
        if (mMenu == null || mMasterImage == null) {
            return;
        }
        //MenuItem undoItem = mMenu.findItem(R.id.undoButton);
        //MenuItem redoItem = mMenu.findItem(R.id.redoButton);
        MenuItem resetItem = mMenu.findItem(R.id.resetHistoryButton);
        MenuItem printItem = mMenu.findItem(R.id.printButton);
        if (!PrintHelper.systemSupportsPrint()) {
            printItem.setVisible(false);
        }
        //mMasterImage.getHistory().setMenuItems(undoItem, redoItem, resetItem);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mShareActionProvider != null) {
            mShareActionProvider.setOnShareTargetSelectedListener(null);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mShareActionProvider != null) {
            mShareActionProvider.setOnShareTargetSelectedListener(this);
        }
        if (SimpleMakeupImageFilter.HAS_TS_MAKEUP) {
            MakeupEngine.getMakeupObj();
        }

        DualCameraNativeEngine.createInstance();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            /*case R.id.undoButton: {
                HistoryManager adapter = mMasterImage.getHistory();
                int position = adapter.undo();
                mMasterImage.onHistoryItemClick(position);
                backToMain();
                invalidateViews();
                return true;
            }
            case R.id.redoButton: {
                HistoryManager adapter = mMasterImage.getHistory();
                int position = adapter.redo();
                mMasterImage.onHistoryItemClick(position);
                invalidateViews();
                return true;
            }*/
            case R.id.resetHistoryButton: {
                resetHistory();
                return true;
            }
            /*case R.id.showImageStateButton: {
                toggleImageStatePanel();
                return true;
            }*/
            case R.id.exportFlattenButton: {
                showExportOptionsDialog();
                return true;
            }
            case android.R.id.home: {
                saveImage();
                return true;
            }
            case R.id.manageUserPresets: {
                manageUserPresets();
                return true;
            }
            case R.id.showInfoPanel: {
                toggleInformationPanel();
                return true;
            }
            case R.id.printButton: {
                print();
                return true;
            }
        }
        return false;
    }

    public void print() {
        Bitmap bitmap = MasterImage.getImage().getHighresImage();
        PrintHelper printer = new PrintHelper(this);
        printer.printBitmap("ImagePrint", bitmap);
    }

    public void addNewPreset() {
        DialogFragment dialog = new PresetManagementDialog();
        dialog.show(getSupportFragmentManager(), "NoticeDialogFragment");
    }

    private void manageUserPresets() {
        DialogFragment dialog = new PresetManagementDialog();
        dialog.show(getSupportFragmentManager(), "NoticeDialogFragment");
    }

    private void showExportOptionsDialog() {
        DialogFragment dialog = new ExportDialog();
        dialog.show(getSupportFragmentManager(), "ExportDialogFragment");
    }

    public void updateUserPresetsFromAdapter(UserPresetsAdapter adapter) {
        ArrayList<FilterUserPresetRepresentation> representations =
                adapter.getDeletedRepresentations();
        for (FilterUserPresetRepresentation representation : representations) {
            deletePreset(representation.getId());
        }
        ArrayList<FilterUserPresetRepresentation> changedRepresentations =
                adapter.getChangedRepresentations();
        for (FilterUserPresetRepresentation representation : changedRepresentations) {
            updatePreset(representation);
        }
        adapter.clearDeletedRepresentations();
        adapter.clearChangedRepresentations();
        loadUserPresets();
    }

    public void loadUserPresets() {
        mUserPresetsManager.load();
        updateUserPresetsFromManager();
    }

    public void updateUserPresetsFromManager() {
        ArrayList<FilterUserPresetRepresentation> presets = mUserPresetsManager.getRepresentations();
        if (presets == null) {
            return;
        }
        if (mCategoryLooksAdapter != null) {
            fillLooks();
        }
        if (presets.size() > 0) {
            mCategoryLooksAdapter.add(new Action(this, Action.SPACER));
        }
        mUserPresetsAdapter.clear();
        for (int i = 0; i < presets.size(); i++) {
            FilterUserPresetRepresentation representation = presets.get(i);
            mCategoryLooksAdapter.add(
                    new Action(this, representation, Action.FULL_VIEW, true));
            mUserPresetsAdapter.add(new Action(this, representation, Action.FULL_VIEW));
        }
        if (presets.size() > 0) {
            mCategoryLooksAdapter.add(new Action(this, Action.ADD_ACTION));
        }
        mCategoryLooksAdapter.notifyDataSetChanged();
        mCategoryLooksAdapter.notifyDataSetInvalidated();
    }

    public void saveCurrentImagePreset(String name) {
        mUserPresetsManager.save(MasterImage.getImage().getPreset(), name);
    }

    private void deletePreset(int id) {
        mUserPresetsManager.delete(id);
    }

    private void updatePreset(FilterUserPresetRepresentation representation) {
        mUserPresetsManager.update(representation);
    }

    public void enableSave(boolean enable) {
        if (mSaveButton != null) {
            mSaveButton.setEnabled(enable);
        }
    }

    private void fillLooks() {
        FiltersManager filtersManager = FiltersManager.getManager();
        ArrayList<FilterRepresentation> filtersRepresentations = filtersManager.getLooks();

        if (mCategoryLooksAdapter != null) {
            mCategoryLooksAdapter.clear();
        }
        mCategoryLooksAdapter = new CategoryAdapter(this);
        int verticalItemHeight = (int) getResources().getDimension(R.dimen.action_item_height);
        mCategoryLooksAdapter.setItemHeight(verticalItemHeight);
        for (FilterRepresentation representation : filtersRepresentations) {
            mCategoryLooksAdapter.add(new Action(this, representation, Action.FULL_VIEW));
        }
        if (mUserPresetsManager.getRepresentations() == null
            || mUserPresetsManager.getRepresentations().size() == 0) {
            mCategoryLooksAdapter.add(new Action(this, Action.ADD_ACTION));
        }

        Fragment panel = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
        if (panel != null) {
            if (panel instanceof MainPanel) {
                MainPanel mainPanel = (MainPanel) panel;
                mainPanel.loadCategoryLookPanel(true);
            }
        }
    }

    public void setDefaultPreset() {
        // Default preset (original)
        ImagePreset preset = new ImagePreset(); // empty
        mMasterImage.setPreset(preset, preset.getLastRepresentation(), true);
    }

    // //////////////////////////////////////////////////////////////////////////////
    // Some utility functions
    // TODO: finish the cleanup.

    public void invalidateViews() {
        for (ImageShow views : mImageViews) {
            views.updateImage();
        }
    }

    public void hideImageViews() {
        for (View view : mImageViews) {
            view.setVisibility(View.GONE);
        }
        mEditorPlaceHolder.hide();
    }

    // //////////////////////////////////////////////////////////////////////////////
    // imageState panel...

    public void toggleImageStatePanel() {
        invalidateOptionsMenu();
        mShowingImageStatePanel = !mShowingImageStatePanel;
        Fragment panel = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
        if (panel != null) {
            if (panel instanceof EditorPanel) {
                EditorPanel editorPanel = (EditorPanel) panel;
                editorPanel.showImageStatePanel(mShowingImageStatePanel);
            } else if (panel instanceof MainPanel) {
                MainPanel mainPanel = (MainPanel) panel;
                mainPanel.showImageStatePanel(mShowingImageStatePanel);
            }
        }
    }

    public void toggleVersionsPanel() {
        mShowingVersionsPanel = !mShowingVersionsPanel;
        Fragment panel = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
        if (panel != null && panel instanceof MainPanel) {
            MainPanel mainPanel = (MainPanel) panel;
            mainPanel.loadCategoryVersionsPanel();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);

        setDefaultValues();
        if (mMasterImage == null) {
            return;
        }
        //loadXML();
        //fillCategories();
        //loadMainPanel();

        if (mCurrentMenu != null) {
            mCurrentMenu.dismiss();
            mCurrentMenu = null;
        }
        if (mCurrentDialog != null) {
            mCurrentDialog.dismiss();
            mCurrentDialog = null;
        }
        // mLoadBitmapTask==null implies you have looked at the intent
        if (!mShowingTinyPlanet && (mLoadBitmapTask == null)) {
            mCategoryFiltersAdapter.removeTinyPlanet();
        }
        stopLoadingIndicator();
    }

    public void setupMasterImage() {

        HistoryManager historyManager = new HistoryManager();
        StateAdapter imageStateAdapter = new StateAdapter(this, 0);
        MasterImage.setMaster(null);
        mMasterImage = MasterImage.getImage();
        mMasterImage.setHistoryManager(historyManager);
        mMasterImage.setStateAdapter(imageStateAdapter);
        mMasterImage.setActivity(this);

        if (Runtime.getRuntime().maxMemory() > LIMIT_SUPPORTS_HIGHRES) {
            mMasterImage.setSupportsHighRes(true);
        } else {
            mMasterImage.setSupportsHighRes(false);
        }
    }

    void resetHistory() {
        HistoryManager adapter = mMasterImage.getHistory();
        adapter.reset();
        HistoryItem historyItem = adapter.getItem(0);
        ImagePreset original = null;
        if (RESET_TO_LOADED) {
            original = new ImagePreset(historyItem.getImagePreset());
        } else {
            original = new ImagePreset();
        }
        FilterRepresentation rep = null;
        if (historyItem != null) {
            rep = historyItem.getFilterRepresentation();
        }
        mMasterImage.setPreset(original, rep, true);
        invalidateViews();
        backToMain();
    }

    public void showDefaultImageView() {
        if(mEditorPlaceHolder != null)
            mEditorPlaceHolder.hide();
        if(mImageShow != null)
            mImageShow.setVisibility(View.VISIBLE);
        if(MasterImage.getImage() != null) {
            MasterImage.getImage().setCurrentFilter(null);
            MasterImage.getImage().setCurrentFilterRepresentation(null);
        }
    }

    public void backToMain() {
        Fragment currentPanel = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
        if (currentPanel instanceof MainPanel) {
            return;
        }
        loadMainPanel();
        showDefaultImageView();
    }

    @Override
    public void onBackPressed() {
       if (imgComparison != null && imgComparison.getVisibility() == View.GONE) {
           imgComparison.setVisibility(View.VISIBLE);
       }
       Fragment currentPanel = getSupportFragmentManager().findFragmentByTag(
                MainPanel.FRAGMENT_TAG);

        if (currentPanel instanceof MainPanel) {
            if (mImageShow.hasModifications()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.unsaved).setTitle(
                        R.string.save_before_exit);
                builder.setPositiveButton(mPopUpText,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                done();
                            }
                        });
                builder.setNegativeButton(mExit,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                HistoryManager adapter = mMasterImage.getHistory();
                                int position = adapter.undoCurrentFilter();
                                mMasterImage.onHistoryItemClick(position);
                                adapter.resetActiveFilter();
                                backToMain();
                                invalidateViews();
                            }
                        });
                builder.show();
            } else {
                done();
            }
            setActionBar(false);
            invalidateOptionsMenu();
            if (MasterImage.getImage().getScaleFactor() < 1)
                setScaleImage(false);
            adjustCompareButton(false);
        } else {
            isComingFromEditorScreen = true;
            backToMain();
        }
    }

    public void cannotLoadImage() {
        Toast.makeText(this, R.string.cannot_load_image, Toast.LENGTH_SHORT).show();
        finish();
    }

    // //////////////////////////////////////////////////////////////////////////////

    public float getPixelsFromDip(float value) {
        Resources r = getResources();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                r.getDisplayMetrics());
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        mMasterImage.onHistoryItemClick(position);
        invalidateViews();
    }

    public void pickImage(int requestCode) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_PICK);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image)),
                requestCode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                Uri selectedImageUri = data.getData();
                startLoadBitmap(selectedImageUri);
            } else if (requestCode == SELECT_FUSION_UNDERLAY) {
                Uri underlayImageUri = data.getData();
                // find fusion representation
                EditorDualCamFusion editor = (EditorDualCamFusion)getEditor(EditorDualCamFusion.ID);
                editor.setUnderlayImageUri(underlayImageUri);
            }
        }
    }


    public void saveImage() {
        if (mImageShow.hasModifications()) {
            // Get the name of the album, to which the image will be saved
            File saveDir = SaveImage.getFinalSaveDirectory(this, mSelectedImageUri);
            int bucketId = GalleryUtils.getBucketId(saveDir.getPath());
            String albumName = LocalAlbum.getLocalizedName(getResources(), bucketId, null);
            mReleaseDualCamOnDestory = false;
            showSavingProgress(albumName);
            mImageShow.saveImage(this, null);
        } else {
            done();
        }
    }


    public void done() {
        hideSavingProgress();
        if (mLoadBitmapTask != null) {
            mLoadBitmapTask.cancel(false);
        }
        mReleaseDualCamOnDestory = true;
        finish();
    }

    private void extractXMPData() {
        XMresults res = XmpPresets.extractXMPData(
                getBaseContext(), mMasterImage, getIntent().getData());
        if (res == null)
            return;

        mOriginalImageUri = res.originalimage;
        mOriginalPreset = res.preset;
    }

    public Uri getSelectedImageUri() {
        return mSelectedImageUri;
    }

    public void setHandlesSwipeForView(View view, float startX, float startY) {
        if (view != null) {
            mHandlingSwipeButton = true;
        } else {
            mHandlingSwipeButton = false;
        }
        mHandledSwipeView = view;
        int[] location = new int[2];
        view.getLocationInWindow(location);
        mSwipeStartX = location[0] + startX;
        mSwipeStartY = location[1] + startY;
    }

    public boolean dispatchTouchEvent (MotionEvent ev) {
        if (mHandlingSwipeButton) {
            int direction = CategoryView.HORIZONTAL;
            if (mHandledSwipeView instanceof CategoryView) {
                direction = ((CategoryView) mHandledSwipeView).getOrientation();
            }
            if (ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float delta = ev.getY() - mSwipeStartY;
                float distance = mHandledSwipeView.getHeight();
                if (direction == CategoryView.VERTICAL) {
                    delta = ev.getX() - mSwipeStartX;
                    mHandledSwipeView.setTranslationX(delta);
                    distance = mHandledSwipeView.getWidth();
                } else {
                    mHandledSwipeView.setTranslationY(delta);
                }
                delta = Math.abs(delta);
                float transparency = Math.min(1, delta / distance);
                mHandledSwipeView.setAlpha(1.f - transparency);
                mHandledSwipeViewLastDelta = delta;
            }
            if (ev.getActionMasked() == MotionEvent.ACTION_CANCEL
                    || ev.getActionMasked() == MotionEvent.ACTION_UP) {
                mHandledSwipeView.setTranslationX(0);
                mHandledSwipeView.setTranslationY(0);
                mHandledSwipeView.setAlpha(1.f);
                mHandlingSwipeButton = false;
                float distance = mHandledSwipeView.getHeight();
                if (direction == CategoryView.VERTICAL) {
                    distance = mHandledSwipeView.getWidth();
                }
                if (mHandledSwipeViewLastDelta > distance) {
                    ((SwipableView) mHandledSwipeView).delete();
                }
            }
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    public Point mHintTouchPoint = new Point();

    public Point hintTouchPoint(View view) {
        int location[] = new int[2];
        view.getLocationOnScreen(location);
        int x = mHintTouchPoint.x - location[0];
        int y = mHintTouchPoint.y - location[1];
        return new Point(x, y);
    }

    public void startTouchAnimation(View target, float x, float y) {
        final CategorySelected hint =
                (CategorySelected) findViewById(R.id.categorySelectedIndicator);
        int location[] = new int[2];
        target.getLocationOnScreen(location);
        mHintTouchPoint.x = (int) (location[0] + x);
        mHintTouchPoint.y = (int) (location[1] + y);
        int locationHint[] = new int[2];
        ((View)hint.getParent()).getLocationOnScreen(locationHint);
        int dx = (int) (x - (hint.getWidth())/2);
        int dy = (int) (y - (hint.getHeight())/2);
        hint.setTranslationX(location[0] - locationHint[0] + dx);
        hint.setTranslationY(location[1] - locationHint[1] + dy);
        hint.setVisibility(View.VISIBLE);
        hint.animate().scaleX(2).scaleY(2).alpha(0).withEndAction(new Runnable() {
            @Override
            public void run() {
                hint.setVisibility(View.INVISIBLE);
                hint.setScaleX(1);
                hint.setScaleY(1);
                hint.setAlpha(1);
            }
        });
    }

    public void setScaleImage(boolean isScaled) {
        mImageShow.scaleImage(isScaled, getBaseContext());
    }
}
