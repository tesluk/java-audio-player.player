/**
 *
 * (c) Sergey Mashkov (aka cy6erGn0m), 2009
 *
 * License: GNU LGPL v3
 * To read license read lgpl-3.0.txt from root of repository or follow URL:
 *      http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 */


package maryb.player;

/**
 *
 * @author cy6erGn0m
 */
public interface PlayerEventListener {

    /**
     * Called by player when end of media reached. Will not happens at stop or
     * pause.
     */
    void endOfMedia();

    /**
     * Happens when player state, playback posion, buffered
     * length or total media length has been changed
     */
    void stateChanged();

    /**
     * Called in case when playback stalled due to low buffering speed
     */
    void buffer();
}
