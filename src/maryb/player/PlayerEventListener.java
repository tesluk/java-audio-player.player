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

    void endOfMedia();

    void stateChanged();

    void buffer();
}
