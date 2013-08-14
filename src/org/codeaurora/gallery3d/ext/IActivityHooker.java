package org.codeaurora.gallery3d.ext;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

/**
 * Activity action hooker class. Host app's activity will call this hooker's
 * functions in its lifecycle. For example:
 * HostActivity.onCreate()-->hooker.onCreate(). But void init(Activity context,
 * Intent intent) will be called before other functions. <br/>
 * IActivityHooker objects may show menus, but we should give a unique menu id
 * to every menus. Hooker can call getMenuActivityId(int) to get a global unique
 * menu id to be used in menu.add(), and can call getMenuOriginalId(int) to get
 * the original menu id. the example: class Hooker implements IActivityHooker {
 * private static final int MENU_EXAMPLE = 1;
 *
 * @Override public boolean onCreateOptionsMenu(Menu menu) {
 *           super.onCreateOptionsMenu(menu); menu.add(0,
 *           getMenuActivityId(MENU_EXAMPLE), 0, android.R.string.ok); return
 *           true; }
 * @Override public boolean onOptionsItemSelected(MenuItem item) {
 *           switch(getMenuOriginalId(item.getItemId())) { case MENU_EXAMPLE:
 *           //do something return true; default: return false; } } }
 */
public interface IActivityHooker {
    /**
     * Will be called in Host Activity.onCreate(Bundle savedInstanceState)
     * @param savedInstanceState
     */
    void onCreate(Bundle savedInstanceState);
    /**
     * Will be called in Host Activity.onStart()
     */
    void onStart();
    /**
     * Will be called in Host Activity.onStop()
     */
    void onStop();
    /**
     * Will be called in Host Activity.onPause()
     */
    void onPause();
    /**
     * Will be called in Host Activity.onResume()
     */
    void onResume();
    /**
     * Will be called in Host Activity.onDestroy()
     */
    void onDestroy();
    /**
     * Will be called in Host Activity.onCreateOptionsMenu(Menu menu)
     * @param menu
     * @return
     */
    /**
     * Will be called in Host Activity.onCreateOptionsMenu(Menu menu)
     *
     * @param menu
     * @return
     */
    boolean onCreateOptionsMenu(Menu menu);

    /**
     * Will be called in Host Activity.onPrepareOptionsMenu(Menu menu)
     *
     * @param menu
     * @return
     */
    boolean onPrepareOptionsMenu(Menu menu);

    /**
     * Will be called in Host Activity.onOptionsItemSelected(MenuItem item)
     *
     * @param item
     * @return
     */
    boolean onOptionsItemSelected(MenuItem item);

    /**
     * Should be called before any other functions.
     *
     * @param context
     * @param intent
     */
    void init(Activity context, Intent intent);

    /**
     * @return return activity set by init(Activity context, Intent intent)
     */
    Activity getContext();

    /**
     * @return return intent set by init(Activity context, Intent intent)
     */
    Intent getIntent();

    /**
     * IActivityHooker objects may show menus, but we should give a unique menu
     * id to every menus. Hooker can call this function to get a global unique
     * menu id to be used in menu.add()
     *
     * @param id
     * @return
     */
    int getMenuActivityId(int id);

    /**
     * When onOptionsItemSelected is called, we can get menu's id from
     * parameter. You can get the original menu id by calling this function.
     *
     * @param id
     * @return
     */
    int getMenuOriginalId(int id);

    /**
     * Host activity will call this function to set parameter to hooker
     * activity.
     *
     * @param key
     * @param value
     */
    void setParameter(String key, Object value);
}
