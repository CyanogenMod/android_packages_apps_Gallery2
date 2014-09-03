package org.codeaurora.gallery3d.video;

import android.app.ListActivity;

import android.app.ActionBar;
import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.android.gallery3d.R;

public class VideoSettingsActivity extends ListActivity {
    private String OPTION_NAME = "option_name";
    private String OPTION_DESC = "option_desc";
    private String DIALOG_TAG_SELECT_STEP_OPTION = "step_option_dialog";

    private static final int STEP_OPTION_DEFAULT = 10;
    private static final int STEP_OPTION_ALTERNATE = 5;
    private static int[] sStepOptionArray =  { STEP_OPTION_DEFAULT, STEP_OPTION_ALTERNATE };

    private static final String SELECTED_STEP_OPTION = "selected_step_option";
    private static final String VIDEO_PLAYER_DATA = "video_player_data";
    private int mSelectedStepOption = -1;
    private SharedPreferences mPrefs = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(getResources().getString(R.string.settings));
        setContentView(R.layout.setting_list);
        ArrayList<HashMap<String, Object>> arrlist = new ArrayList<HashMap<String, Object>>(1);
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put(OPTION_NAME, getString(R.string.step_option_name));
        map.put(OPTION_DESC, getString(R.string.step_option_desc));
        arrlist.add(map);
        SimpleAdapter adapter = new SimpleAdapter(this, arrlist, android.R.layout.simple_expandable_list_item_2,
                new String[] { OPTION_NAME, OPTION_DESC }, new int[] {
                android.R.id.text1,  android.R.id.text2});
        setListAdapter(adapter);
        restoreStepOptionSettings();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        storeStepOptionSettings();
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        restoreDialogFragment();
        restoreStepOptionSettings();
    }
    
    

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        storeStepOptionSettings();
        super.onDestroy();
    }

    @Override
    protected void onListItemClick(ListView arg0, View arg1, int arg2, long arg3) {
        switch (arg2) {
        case 0:
            DialogFragment newFragment = null;
            FragmentManager fragmentManager = getFragmentManager();
            removeOldFragmentByTag(DIALOG_TAG_SELECT_STEP_OPTION);
            newFragment = StepOptionDialogFragment.newInstance(getStepOptionIDArray(),
                    R.string.step_option_name, mSelectedStepOption);
            ((StepOptionDialogFragment) newFragment).setOnClickListener(mStepOptionSelectedListener);
            newFragment.show(fragmentManager, DIALOG_TAG_SELECT_STEP_OPTION);
            break;
        default:
            break;
        }
    }
    
    private int[] getStepOptionIDArray() {
        return new int[] { R.string.step_option_default, R.string.step_option_alternate };
    }

    private DialogInterface.OnClickListener mStepOptionSelectedListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int whichItemSelect) {
            mSelectedStepOption = whichItemSelect;
            dialog.dismiss();
        }
    };
    
    /**
     * remove old DialogFragment
     * 
     * @param tag
     *            the tag of DialogFragment to be removed
     */
    private void removeOldFragmentByTag(String tag) {
        FragmentManager fragmentManager = getFragmentManager();
        DialogFragment oldFragment = (DialogFragment) fragmentManager.findFragmentByTag(tag);
        if (null != oldFragment) {
            oldFragment.dismissAllowingStateLoss();
        }
    }
    
    private void restoreDialogFragment() {
        FragmentManager fragmentManager = getFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag(DIALOG_TAG_SELECT_STEP_OPTION);
        if (null != fragment) {
            ((StepOptionDialogFragment) fragment).setOnClickListener(mStepOptionSelectedListener);
        }
    }
    
    private void storeStepOptionSettings() {
        if (null == mPrefs) {
            mPrefs = getSharedPreferences(VIDEO_PLAYER_DATA, 0);
        }
        SharedPreferences.Editor ed = mPrefs.edit();
        ed.clear();
        ed.putInt(SELECTED_STEP_OPTION, mSelectedStepOption);
        ed.commit();
    }

    private void restoreStepOptionSettings() {
        if (null == mPrefs) {
            mPrefs = getSharedPreferences(VIDEO_PLAYER_DATA, 0);
        }
        mSelectedStepOption = mPrefs.getInt(SELECTED_STEP_OPTION, 0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            // The user clicked on the Messaging icon in the action bar. Take them back from
            // wherever they came from
            finish();
            return true;
        }
        return false;
    }

    public static int getStepOptionValue(final Context context) {
        final SharedPreferences mPrefs = context.getSharedPreferences(VIDEO_PLAYER_DATA, 0);
        int selectedStepOption = mPrefs.getInt(SELECTED_STEP_OPTION, 0);
        return sStepOptionArray[selectedStepOption] * 1000;
    }
}
