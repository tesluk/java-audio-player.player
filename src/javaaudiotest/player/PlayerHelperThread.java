package javaaudiotest.player;

import javax.sound.sampled.SourceDataLine;

/**
 *
 * @author cy6ergn0m
 */
public class PlayerHelperThread extends Thread {

    private final Player parent;

    public PlayerHelperThread( Player parent ) {
        super( "player-helper-thread" );
        this.parent = parent;
    }

    private volatile boolean dieRequested = false;

    private boolean isStopped() {
        return parent.getState() == PlayerState.PAUSED || parent.getState() == PlayerState.STOPPED;
    }

    @Override
    public void run() {
        try {
            SourceDataLine line;
            int available;
            int size;
            float rate;
            long time;
            while( !dieRequested && !isStopped() ) {
                sleep( 150 );
                if( ( line = parent.getCurrentDataLine() ) != null ) {
                    size = line.getBufferSize();
                    available = line.available();
                    time = line.getMicrosecondPosition();

                    parent.currentPlayTimeMcsec = time;

                    rate = ( (float) available ) / ( (float) size );
                    synchronized( parent.osync ) {
                        PlayerState state = parent.getState();
                        if( rate > 0.9 ) {
                            if( state == PlayerState.PLAYING && !parent.getPumpStream().isAllDownloaded() ) {
                                System.out.println( "low speed detected.. lets pause" );
                                state = PlayerState.PAUSED_BUFFERING;
                                parent.osync.notifyAll();
                                continue;
                                //line.stop();   // !! NEVER STOP LINE to prevent player-playback-thread spinning on Linux
                                }
                        }
                        if( state == PlayerState.PAUSED_BUFFERING ) {
                            if( ( /*rate == 0. && */ ( ( parent.currentBufferedTimeMcsec - parent.currentPlayTimeMcsec ) > 10000000L ) ) || parent.getPumpStream().isAllDownloaded() ) {
                                // NOTE: rate unchecked, because in this state player-playback-thread sleeping and can't fill device buffer
                                System.out.println( "lets resume" );
                                parent.osync.wait( 500 );
                                state = PlayerState.PLAYING;
                                parent.osync.notifyAll();
                                parent.osync.wait( 500 );
                                //line.start();
                                }
                        }
                    }

                }
            }
        } catch( InterruptedException ignore ) {
        } finally {
        }
    }

    public void die() {
        dieRequested = true;
        interrupt();
    }

}
