package org.codeaurora.gallery3d.ext;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;

/**
 * The composite pattern class. It will deliver every action to its leaf
 * hookers.
 */
public class ActivityHookerGroup extends ActivityHooker {
    private ArrayList<IActivityHooker> mHooks = new ArrayList<IActivityHooker>();

    /**
     * Add hooker to current group.
     *
     * @param hooker
     * @return
     */
    public boolean addHooker(IActivityHooker hooker) {
        return mHooks.add(hooker);
    }

    /**
     * Remove hooker from current group.
     *
     * @param hooker
     * @return
     */
    public boolean removeHooker(IActivityHooker hooker) {
        return mHooks.remove(hooker);
    }

    /**
     * Get hooker of requested location.
     *
     * @param index
     * @return
     */
    public IActivityHooker getHooker(int index) {
        return mHooks.get(index);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        for (IActivityHooker hook : mHooks) {
            hook.onCreate(savedInstanceState);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        for (IActivityHooker hook : mHooks) {
            hook.onStart();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        for (IActivityHooker hook : mHooks) {
            hook.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        for (IActivityHooker hook : mHooks) {
            hook.onPause();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        for (IActivityHooker hook : mHooks) {
            hook.onStop();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (IActivityHooker hook : mHooks) {
            hook.onDestroy();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        boolean handle = false;
        for (IActivityHooker hook : mHooks) {
            boolean one = hook.onCreateOptionsMenu(menu);
            if (!handle) {
                handle = one;
            }
        }
        return handle;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        boolean handle = false;
        for (IActivityHooker hook : mHooks) {
            boolean one = hook.onPrepareOptionsMenu(menu);
            if (!handle) {
                handle = one;
            }
        }
        return handle;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        boolean handle = false;
        for (IActivityHooker hook : mHooks) {
            boolean one = hook.onOptionsItemSelected(item);
            if (!handle) {
                handle = one;
            }
        }
        return handle;
    }

    @Override
    public void setParameter(String key, Object value) {
        super.setParameter(key, value);
        for (IActivityHooker hook : mHooks) {
            hook.setParameter(key, value);
        }
    }

    @Override
    public void init(Activity context, Intent intent) {
        super.init(context, intent);
        for (IActivityHooker hook : mHooks) {
            hook.init(context, intent);
        }
    }
}
