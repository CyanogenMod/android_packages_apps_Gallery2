package org.codeaurora.gallery3d.ext;

public interface IMoviePlayer {
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
}
