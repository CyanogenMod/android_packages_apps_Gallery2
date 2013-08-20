package org.codeaurora.gallery3d.ext;

public interface IMoviePlayer {

    /**
     * add new bookmark Uri.
     */
    void addBookmark();

    /**
     * Loop current video.
     *
     * @param loop
     */
    void setLoop(boolean loop);

    /**
     * Loop current video or not
     *
     * @return
     */
    boolean getLoop();

    /**
     * Can stop current video or not.
     *
     * @return
     */
    boolean canStop();

    /**
     * Stop current video.
     */
    void stopVideo();

    /**
     * start current item and stop playing video.
     *
     * @param item
     */
    void startNextVideo(IMovieItem item);
}
