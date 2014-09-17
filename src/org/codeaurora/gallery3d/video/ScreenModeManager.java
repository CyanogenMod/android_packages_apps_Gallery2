package org.codeaurora.gallery3d.video;

import android.util.Log;

import java.util.ArrayList;

public class ScreenModeManager {
    private static final String TAG = "ScreenModeManager";
    private static final boolean LOG = false;
    //support screen mode.
    public static final int SCREENMODE_BIGSCREEN = 1;
    public static final int SCREENMODE_FULLSCREEN = 2;
    public static final int SCREENMODE_CROPSCREEN = 4;
    public static final int SCREENMODE_ALL = 7;
    
    private int mScreenMode = SCREENMODE_BIGSCREEN;
    private int mScreenModes = SCREENMODE_ALL;
    
    /**
     * Enable specified screen mode list. 
     * The screen mode's value determines the order of being shown. 
     * <br>you can enable three screen modes by setting screenModes = 
     * {@link #SCREENMODE_BIGSCREEN} | 
     * {@link #SCREENMODE_FULLSCREEN} |
     * {@link #SCREENMODE_CROPSCREEN} or 
     * just enable two screen modes by setting screenModes = 
     * {@link #SCREENMODE_BIGSCREEN} | 
     * {@link #SCREENMODE_CROPSCREEN}.
     * <br>If current screen mode is the last one of the ordered list, 
     * then the next screen mode will be the first one of the ordered list.
     * @param screenModes enabled screen mode list.
     */
    public void setScreenModes(final int screenModes) {
        mScreenModes = (SCREENMODE_BIGSCREEN & screenModes)
            | (SCREENMODE_FULLSCREEN & screenModes)
            | (SCREENMODE_CROPSCREEN & screenModes);
        if ((screenModes & SCREENMODE_ALL) == 0) {
            mScreenModes = SCREENMODE_ALL;
            Log.w(TAG, "wrong screenModes=" + screenModes + ". use default value " + SCREENMODE_ALL);
        }
        if (LOG) {
            Log.v(TAG, "enableScreenMode(" + screenModes + ") mScreenModes=" + mScreenModes);
        }
    }
    
    /**
     * Get the all screen modes of media controller.
     * <br><b>Note:</b> it is not the video's current screen mode.
     * @return the current screen modes.
     */
    public int getScreenModes() {
        return mScreenModes;
    }
    
    public void setScreenMode(final int curScreenMode) {
        if (LOG) {
            Log.v(TAG, "setScreenMode(" + curScreenMode + ")");
        }
        mScreenMode = curScreenMode;
        for (final ScreenModeListener listener : mListeners) {
            listener.onScreenModeChanged(curScreenMode);
        }
    }
    
    public int getScreenMode() {
        if (LOG) {
            Log.v(TAG, "getScreenMode() return " + mScreenMode);
        }
        return mScreenMode;
    }
    
    public int getNextScreenMode() {
        int mode = getScreenMode();
        mode <<= 1;
        if ((mode & mScreenModes) == 0) {
            //not exist, find the right one
            if (mode > mScreenModes) {
                mode = 1;
            }
            while ((mode & mScreenModes) == 0) {
                mode <<= 1;
                if (mode > mScreenModes) {
                    throw new RuntimeException("wrong screen mode = " + mScreenModes);
                }
            }
        }
        if (LOG) {
            Log.v(TAG, "getNextScreenMode() = " + mode);
        }
        return mode;
    }
    
    private final ArrayList<ScreenModeListener> mListeners = new ArrayList<ScreenModeListener>();
    public void addListener(final ScreenModeListener l) {
        if (!mListeners.contains(l)) {
            mListeners.add(l);
        }
        if (LOG) {
            Log.v(TAG, "addListener(" + l + ")");
        }
    }
    
    public void removeListener(final ScreenModeListener l) {
        mListeners.remove(l);
        if (LOG) {
            Log.v(TAG, "removeListener(" + l + ")");
        }
    }
    
    public void clear() {
        mListeners.clear();
    }
    
    public interface ScreenModeListener {
        void onScreenModeChanged(int newMode);
    }
}
