/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.gallery3d.filtershow.category;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.filters.HazeBusterActs;
import com.android.gallery3d.filtershow.filters.SeeStraightActs;
import com.android.gallery3d.filtershow.filters.SimpleMakeupImageFilter;
import com.android.gallery3d.filtershow.filters.TrueScannerActs;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.state.StatePanel;
import com.android.gallery3d.filtershow.tools.DualCameraNativeEngine.DdmStatus;

public class MainPanel extends Fragment {

    private static final String LOGTAG = "MainPanel";

    private LinearLayout mMainView;
    private ImageButton looksButton;
    private ImageButton bordersButton;
    private ImageButton geometryButton;
    private ImageButton filtersButton;
    private ImageButton dualCamButton;
    private ImageButton makeupButton;
    private ImageButton trueScannerButton;
    private ImageButton hazeBusterButton;
    private ImageButton seeStraightButton;

    public static final String FRAGMENT_TAG = "MainPanel";
    public static final String EDITOR_TAG = "coming-from-editor-panel";
    public static final int LOOKS = 0;
    public static final int BORDERS = 1;
    public static final int GEOMETRY = 2;
    public static final int FILTERS = 3;
    public static final int MAKEUP = 4;
    public static final int DUALCAM = 5;
    public static final int VERSIONS = 6;
    public static final int TRUESCANNER = 7;
    public static final int HAZEBUSTER = 8;
    public static final int SEESTRAIGHT = 9;

    private int mCurrentSelected = -1;
    private int mPreviousToggleVersions = -1;
    private boolean isEffectClicked;

    private void selection(int position, boolean value) {
        if (value) {
            FilterShowActivity activity = (FilterShowActivity) getActivity();
            activity.setCurrentPanel(position);
        }
        switch (position) {
            case LOOKS: {
                looksButton.setSelected(value);
                break;
            }
            case BORDERS: {
                bordersButton.setSelected(value);
                break;
            }
            case GEOMETRY: {
                geometryButton.setSelected(value);
                break;
            }
            case FILTERS: {
                filtersButton.setSelected(value);
                break;
            }
            case MAKEUP: {
                if(makeupButton != null) {
                    makeupButton.setSelected(value);
                }
                break;
            }
            case DUALCAM: {
                dualCamButton.setSelected(value);
                break;
            }
            case TRUESCANNER: {
                break;
            }
            case HAZEBUSTER: {
                break;
            }
            case SEESTRAIGHT: {
                break;
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mMainView != null) {
            if (mMainView.getParent() != null) {
                ViewGroup parent = (ViewGroup) mMainView.getParent();
                parent.removeView(mMainView);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mMainView = (LinearLayout) inflater.inflate(
                R.layout.filtershow_main_panel, null, false);

        looksButton = (ImageButton) mMainView.findViewById(R.id.fxButton);
        bordersButton = (ImageButton) mMainView.findViewById(R.id.borderButton);
        geometryButton = (ImageButton) mMainView.findViewById(R.id.geometryButton);
        filtersButton = (ImageButton) mMainView.findViewById(R.id.colorsButton);
        dualCamButton = (ImageButton) mMainView.findViewById(R.id.dualCamButton);
        if (SimpleMakeupImageFilter.HAS_TS_MAKEUP) {
            makeupButton = (ImageButton) mMainView.findViewById(R.id.makeupButton);
            makeupButton.setVisibility(View.VISIBLE);
        }

        if (makeupButton != null) {
            makeupButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPanel(MAKEUP);
                }
            });
        }
        trueScannerButton = (ImageButton) mMainView.findViewById(R.id.trueScannerButton);
        if (!TrueScannerActs.isTrueScannerEnabled()) {
            trueScannerButton.setVisibility(View.GONE);
        }

        hazeBusterButton = (ImageButton) mMainView.findViewById(R.id.hazeBusterButton);
        if(!HazeBusterActs.isHazeBusterEnabled()) {
            hazeBusterButton.setVisibility(View.GONE);
        }

        seeStraightButton = (ImageButton) mMainView.findViewById(R.id.seeStraightButton);
        if(!SeeStraightActs.isSeeStraightEnabled()) {
            seeStraightButton.setVisibility(View.GONE);
        }

        looksButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(LOOKS);
            }
        });
        bordersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(BORDERS);
            }
        });
        geometryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(GEOMETRY);
            }
        });
        filtersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(FILTERS);
            }
        });
        dualCamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(DUALCAM);
            }
        });

        updateDualCameraButton();

        trueScannerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(TRUESCANNER);
            }
        });

        hazeBusterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(HAZEBUSTER);
            }
        });

        seeStraightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(SEESTRAIGHT);
            }
        });

        FilterShowActivity activity = (FilterShowActivity) getActivity();
//        showImageStatePanel(activity.isShowingImageStatePanel());
        showPanel(activity.getCurrentPanel());
        return mMainView;
    }

    private boolean isRightAnimation(int newPos) {
        if (newPos < mCurrentSelected) {
            return false;
        }
        return true;
    }

    private void setCategoryFragment(CategoryPanel category, boolean fromRight) {
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        FilterShowActivity activity = (FilterShowActivity) getActivity();
        if (fromRight) {
            transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_right);
        } else {
            transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_left);
        }
        if (isEffectClicked) {
            activity.setActionBar(true);
            isEffectClicked = false;
        } else {
            activity.setActionBar(false);
        }
        transaction.replace(R.id.category_panel_container, category, CategoryPanel.FRAGMENT_TAG);
        transaction.commitAllowingStateLoss();
    }

    public void loadCategoryLookPanel(boolean force) {
        if (!force && mCurrentSelected == LOOKS) {
            return;
        }
        boolean fromRight = isRightAnimation(LOOKS);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(LOOKS);
        setCategoryFragment(categoryPanel, fromRight);
        mCurrentSelected = LOOKS;
        selection(mCurrentSelected, true);
    }

    public void loadCategoryBorderPanel() {
        if (mCurrentSelected == BORDERS) {
            return;
        }
        boolean fromRight = isRightAnimation(BORDERS);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(BORDERS);
        setCategoryFragment(categoryPanel, fromRight);
        mCurrentSelected = BORDERS;
        selection(mCurrentSelected, true);
    }

    public void loadCategoryMakeupPanel() {
        if (makeupButton == null) {
            return;
        }
        boolean fromRight = isRightAnimation(MAKEUP);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(MAKEUP);
        setCategoryFragment(categoryPanel, fromRight);
        mCurrentSelected = MAKEUP;
        selection(mCurrentSelected, true);
    }

    public void loadCategoryGeometryPanel() {
        if (mCurrentSelected == GEOMETRY) {
            return;
        }
        if (MasterImage.getImage().hasTinyPlanet()) {
            return;
        }
        boolean fromRight = isRightAnimation(GEOMETRY);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(GEOMETRY);
        setCategoryFragment(categoryPanel, fromRight);
        mCurrentSelected = GEOMETRY;
        selection(mCurrentSelected, true);
    }

    public void loadCategoryTrueScannerPanel() {
        boolean fromRight = isRightAnimation(TRUESCANNER);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(TRUESCANNER);
        setCategoryFragment(categoryPanel, fromRight);
        mCurrentSelected = TRUESCANNER;
        selection(mCurrentSelected, true);
    }

    public void loadCategoryHazeBusterPanel() {
        boolean fromRight = isRightAnimation(HAZEBUSTER);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(HAZEBUSTER);
        setCategoryFragment(categoryPanel, fromRight);
        mCurrentSelected = HAZEBUSTER;
        selection(mCurrentSelected, true);
    }

    public void loadCategorySeeStraightPanel() {
        boolean fromRight = isRightAnimation(SEESTRAIGHT);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(SEESTRAIGHT);
        setCategoryFragment(categoryPanel, fromRight);
        mCurrentSelected = SEESTRAIGHT;
        selection(mCurrentSelected, true);
    }

    public void loadCategoryFiltersPanel() {
        if (mCurrentSelected == FILTERS) {
            return;
        }
        boolean fromRight = isRightAnimation(FILTERS);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(FILTERS);
        setCategoryFragment(categoryPanel, fromRight);
        mCurrentSelected = FILTERS;
        selection(mCurrentSelected, true);
    }

    public void loadCategoryVersionsPanel() {
        if (mCurrentSelected == VERSIONS) {
            return;
        }
        FilterShowActivity activity = (FilterShowActivity) getActivity();
        activity.updateVersions();
        boolean fromRight = isRightAnimation(VERSIONS);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(VERSIONS);
        setCategoryFragment(categoryPanel, fromRight);
        mCurrentSelected = VERSIONS;
        selection(mCurrentSelected, true);
    }

    public void loadCategoryDualCamPanel() {
        if (mCurrentSelected == DUALCAM) {
            return;
        }
        boolean fromRight = isRightAnimation(DUALCAM);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(DUALCAM);
        setCategoryFragment(categoryPanel, fromRight);
        mCurrentSelected = DUALCAM;
        selection(mCurrentSelected, true);

        if(MasterImage.getImage().isDepthMapLoadingDone() == false) {
            FilterShowActivity activity = (FilterShowActivity) getActivity();
            if(activity.isLoadingVisible() == false)
                activity.startLoadingIndicator();
        }
    }

    public void showPanel(int currentPanel) {
        isEffectClicked = true;
        FilterShowActivity activity = (FilterShowActivity) getActivity();
        switch (currentPanel) {
            case LOOKS: {
                loadCategoryLookPanel(false);
                break;
            }
            case BORDERS: {
                loadCategoryBorderPanel();
                break;
            }
            case GEOMETRY: {
                loadCategoryGeometryPanel();
                break;
            }
            case FILTERS: {
                loadCategoryFiltersPanel();
                break;
            }
            case DUALCAM: {
                loadCategoryDualCamPanel();
                break;
            }
            case TRUESCANNER: {
                loadCategoryTrueScannerPanel();
                break;
            }
            case HAZEBUSTER: {
                loadCategoryHazeBusterPanel();
                break;
            }
            case SEESTRAIGHT: {
                loadCategorySeeStraightPanel();
                break;
            }
            case VERSIONS: {
                loadCategoryVersionsPanel();
                break;
            }
            case MAKEUP: {
                loadCategoryMakeupPanel();
                break;
            }
        }
        if (currentPanel > 0) {
            activity.adjustCompareButton(true);
        } else {
            activity.adjustCompareButton(false);
        }
    }

    public void setToggleVersionsPanelButton(ImageButton button) {
        if (button == null) {
            return;
        }
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCurrentSelected == VERSIONS) {
                    showPanel(mPreviousToggleVersions);
                } else {
                    mPreviousToggleVersions = mCurrentSelected;
                    showPanel(VERSIONS);
                }
            }
        });
    }

    public void showImageStatePanel(boolean show) {
        View container = mMainView.findViewById(R.id.state_panel_container);
        FragmentTransaction transaction = null;
        if (container == null) {
            FilterShowActivity activity = (FilterShowActivity) getActivity();
            container = activity.getMainStatePanelContainer(R.id.state_panel_container);
        } else {
            transaction = getChildFragmentManager().beginTransaction();
        }
        if (container == null) {
            return;
        } else {
            transaction = getFragmentManager().beginTransaction();
        }
        int currentPanel = mCurrentSelected;
        if (show) {
            container.setVisibility(View.VISIBLE);
            StatePanel statePanel = new StatePanel();
            statePanel.setMainPanel(this);
            FilterShowActivity activity = (FilterShowActivity) getActivity();
            activity.updateVersions();
            transaction.replace(R.id.state_panel_container, statePanel, StatePanel.FRAGMENT_TAG);
        } else {
            container.setVisibility(View.GONE);
            Fragment statePanel = getChildFragmentManager().findFragmentByTag(StatePanel.FRAGMENT_TAG);
            if (statePanel != null) {
                transaction.remove(statePanel);
            }
            if (currentPanel == VERSIONS) {
                currentPanel = LOOKS;
            }
        }
        mCurrentSelected = -1;
        showPanel(currentPanel);
        transaction.commit();
    }

    public void updateDualCameraButton() {
        if(dualCamButton != null) {
            DdmStatus status = MasterImage.getImage().getDepthMapLoadingStatus();
            boolean enable = (status == DdmStatus.DDM_LOADING || 
                               status == DdmStatus.DDM_LOADED);
            dualCamButton.setVisibility(enable?View.VISIBLE:View.GONE);
        }
    }
}
