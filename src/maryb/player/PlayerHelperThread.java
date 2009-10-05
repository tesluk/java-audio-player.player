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

import javax.sound.sampled.SourceDataLine;

/**
 *
 * @author cy6ergn0m
 */
/* package */ class PlayerHelperThread extends Thread {

    private final Player parent;

    private final PlayerThread creator;

    public PlayerHelperThread( Player parent, PlayerThread creator ) {
        super( "player-helper-thread" );
        this.parent = parent;
        this.creator = creator;
    }

    private volatile boolean dieRequested = false;

    private boolean isStopped() {
        return parent.getState() == PlayerState.PAUSED || parent.getState() == PlayerState.STOPPED;
    }

    private boolean aliveCriterion() {
        return !dieRequested &&
                !isStopped() &&
                creator.isAlive();
    }

    @Override
    public void run() {
        try {
            SourceDataLine line;
            int available;
            int size;
            float rate;
            long time;
            long now, lpa, timeToSleep;

            while( aliveCriterion() ) {
                now = System.currentTimeMillis();
                lpa = parent.lastPlayerActivity;
                timeToSleep = 400 - ( now - lpa );
                if( timeToSleep < 200 )
                    timeToSleep = 200;

                sleep( timeToSleep );

                if( parent.lastPlayerActivity == lpa ) {
                    if( ( line = parent.getCurrentDataLine() ) != null ) {
                        size = line.getBufferSize();
                        available = line.available();

                        rate = ( (float) available ) / ( (float) size );
                        final PlayerState state = parent.getState();
                        if( state == PlayerState.PAUSED_BUFFERING || rate > 0.9 ) {
                            time = parent.getCurrentPosition();

                            synchronized( parent.osync ) {
                                if( rate > 0.9 ) {
                                    if( state == PlayerState.PLAYING && !parent.getPumpStream().isAllDownloaded() ) {
                                        System.out.println( "low speed detected.. lets pause" );
                                        parent.setState( PlayerState.PAUSED_BUFFERING );
                                        parent.osync.notifyAll();
                                        continue;
                                        //line.stop();   // !! NEVER STOP LINE to prevent player-playback-thread spinning on Linux
                                    }
                                }
                                if( state == PlayerState.PAUSED_BUFFERING ) {
                                    if( ( /*rate == 0. && */ ( ( parent.currentBufferedTimeMcsec - time ) > 10000000L ) ) || parent.getPumpStream().isAllDownloaded() ) {
                                        // NOTE: rate unchecked, because in this state player-playback-thread sleeping and can't fill device buffer
                                        System.out.println( "lets resume" );
                                        parent.osync.wait( 500 );
                                        parent.setState( PlayerState.PLAYING );
                                        parent.osync.notifyAll();
                                        parent.osync.wait( 500 );
                                        //line.start();
                                    }
                                }
                            }
                        }

                    }
                }

            }
        } catch( InterruptedException ignore ) {
        } finally {
            if( creator.isAlive() )
                creator.die();
            //else if( !isStopped() )    // FIX for BUG 1.
            //    parent.setStateNotify( PlayerState.STOPPED );
        }
    }

    public void die() {
        dieRequested = true;
        interrupt();
    }

}
