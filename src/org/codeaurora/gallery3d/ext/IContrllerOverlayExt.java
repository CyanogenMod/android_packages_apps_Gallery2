package org.codeaurora.gallery3d.ext;
/**
 * Controller overlay extension interface.
 */
public interface IContrllerOverlayExt {
    /**
     * Show buffering state.
     * @param fullBuffer
     * @param percent
     */
    void showBuffering(boolean fullBuffer, int percent);
    /**
     * Clear buffering state.
     */
    void clearBuffering();
    /**
     * Show re-connecting state.
     * @param times
     */
    void showReconnecting(int times);
    /**
     * Show re-connecting error for connecting fail error.
     */
    void showReconnectingError();
    /**
     * Show playing info or not.
     * @param liveStreaming true means showing playing info, otherwise doesn't show playing info.
     */
    void setPlayingInfo(boolean liveStreaming);
    /**
     * Indicates whether current video can be paused or not.
     * @param canPause
     */
    void setCanPause(boolean canPause);
    /**
     * Indicates whether thumb can be scrubbed or not. 
     * @param enable
     */
    void setCanScrubbing(boolean enable);
    /**
     * Always show bottmon panel or not.
     * @param alwaysShow
     * @param foreShow
     */
    void setBottomPanel(boolean alwaysShow, boolean foreShow);
    /**
     * Is playing end or not.
     * @return
     */
    boolean isPlayingEnd();
}
