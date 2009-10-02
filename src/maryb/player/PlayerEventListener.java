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
