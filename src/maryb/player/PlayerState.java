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
public enum PlayerState {
    /**
     * Player stopped
     */
    STOPPED,
    /**
     * Player currently paused. Please note that actuially there is no any 
     * difference between stop and pause but rusume playback from pause.
     */
    PAUSED,
    /**
     * Playback is running
     */
    PLAYING,
    /**
     * Playback was started but stalled. This may happen when remote resource
     * connected via slow connection or remote server under high load. Player
     * restores itself to PLAYING when buffed length will grow.
     */
    PAUSED_BUFFERING
}
