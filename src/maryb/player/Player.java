package maryb.player;

import maryb.player.io.SeekablePumpStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

/**
 *
 * @author cy6erGn0m
 */
public final class Player {

    // public-managed values
    private float currentVolume = 0.5f;

    private String sourceLocation = null;

    private volatile PlayerState state = PlayerState.STOPPED;

    /* package */ volatile long currentBufferedTimeMcsec = 0;

    /* package */ volatile long currentPlayTimeMcsec = 0;

    /* package */ volatile long currentSeekPositionMcsec = 0;

    /* package */ volatile long totalPlayTimeMcsec = 0;

    private PlayerEventListener listener;

    /* package */ volatile long lastPlayerActivity = 0;

    /* package */ boolean endOfMediaReached = false;

    public float getCurrentVolume() {
        return currentVolume;
    }

    public void setCurrentVolume( float currentVolume ) {
        if( currentVolume > 1. || currentVolume < 0 )
            throw new IllegalArgumentException( "volume should be non-negative and less or equal to 1.0" );
        this.currentVolume = currentVolume;

        SourceDataLine line = currentDataLine;
        if( line != null )
            populateVolume( line );
    }

    public String getSourceLocation() {
        return sourceLocation;
    }

    public void setSourceLocation( String sourceLocation ) {
        if( this.sourceLocation == null )
            this.sourceLocation = sourceLocation;
        else if( this.sourceLocation.equals( sourceLocation ) )
            return;

        this.sourceLocation = sourceLocation;

        SeekablePumpStream pump;
        InputStream is;

        stop();
        synchronized( osync ) {

            currentBufferedTimeMcsec = 0;
            currentPlayTimeMcsec = 0;
            currentSeekPositionMcsec = 0;

            pump = pumpStream;
            is = realInputStream;

            pumpStream = null;
            is = null;

            osync.notifyAll();
        }

        close( pump );
        close( is );

    }

    public PlayerState getState() {
        return state;
    }

    /* package */ void setState( PlayerState state ) {
        this.state = state;
    }

    public long getCurrentBufferedTimeMcsec() {
        return currentBufferedTimeMcsec;
    }

    public long getCurrentPosition() {
        return currentPlayTimeMcsec + currentSeekPositionMcsec;
    }

    public long getTotalPlayTimeMcsec() {
        return totalPlayTimeMcsec;
    }

    public void setListener( PlayerEventListener listener ) {
        this.listener = listener;
    }

    public PlayerEventListener getListener() {
        return listener;
    }

    public boolean isEndOfMediaReached() {
        return endOfMediaReached;
    }
    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // internal data

    /* package */ final Object osync = new Object();

    /* package */ volatile SourceDataLine currentDataLine;

    /* package */ final AtomicReference<PlayerThread> currentPlayerThread = new AtomicReference<PlayerThread>();
    
    /* package */ int realInputStreamLength;

    /* package */ SourceDataLine getCurrentDataLine() {
        return currentDataLine;
    }

    /* package */ SeekablePumpStream getPumpStream() {
        return pumpStream;
    }

    /* package */ void setPumpStream( SeekablePumpStream pumpStream ) {
        this.pumpStream = pumpStream;
    }

    /* package */ void populateVolume( SourceDataLine line ) {
        if( line != null && line.isControlSupported( FloatControl.Type.MASTER_GAIN ) ) {
            FloatControl c = (FloatControl) line.getControl( FloatControl.Type.MASTER_GAIN );
            float interval = c.getMaximum() - c.getMinimum();
            float cv = currentVolume;

            interval *= cv;
            c.setValue( c.getMinimum() + interval );
        }
    }
//
//    public void debugPlayStreamStart( InputStream is ) {
//        Bitstream bs = new Bitstream( is );
//
//        synchronized( osync ) {
//            stop();
//
//            currentPlayerThread = new PlayerThread( bs, Integer.MAX_VALUE );
//            currentPlayerThread.start();
//        }
//    }

    public void play() {
        if( state == PlayerState.PLAYING )
            return;
        PlayerThread th = currentPlayerThread.get();

        if( th == null ) {
            th = new PlayerThread( Integer.MAX_VALUE, this );
            if( currentPlayerThread.compareAndSet( null, th ) )
                th.start();
        }

    }

    private void waitForState( PlayerState st ) throws InterruptedException {
        synchronized( osync ) {
            while( state != st ) {
                osync.wait();
            }
        }
    }

    public void playSync() throws InterruptedException {
        play();
        waitForState( PlayerState.PLAYING );
    }

    private void interruptPlayback( PlayerState st ) {
        if( state == st )
            return;

        PlayerThread th = currentPlayerThread.get();
        if( th != null ) {
            th.setRequestedState( st );
            th.die();
        }

    }

    public void stop() {
        interruptPlayback( PlayerState.STOPPED );
        currentSeekPositionMcsec = 0;
        currentPlayTimeMcsec = 0;
        SourceDataLine l = currentDataLine;
        if( l != null ) {
            try {
                l.stop();
            } catch( Throwable ignoredT ) { // be silent
            }
        }
    }

    public void stopSync() throws InterruptedException {
        stop();
        waitForState( PlayerState.STOPPED );
    }

    public void pause() {
        interruptPlayback( PlayerState.PAUSED );
    }

    public void pauseSync() throws InterruptedException {
        pause();
        waitForState( PlayerState.PAUSED );
    }

    private static void close( Closeable c ) {
        try {
            if( c != null )
                c.close();
        } catch( IOException ignore ) {
        } catch( Throwable t ) {
            t.printStackTrace();
        }
    }

    private static void close( SeekablePumpStream sps ) {
        try {
            if( sps != null )
                sps.realClose();
        } catch( IOException ignore ) {
        } catch( Throwable t ) {
            t.printStackTrace();
        }
    }

//    private LineListener lineListener = new LineListener() {
//
//        public void update( LineEvent event ) {
//            System.out.println( "line event: " + event );
//            System.out.println( "line event type: " + event.getType() );
//            System.out.println( "line event pos: " + event.getFramePosition() );
//            System.out.println( "line event thread: " + Thread.currentThread().getName() );
//        }
//
//    };

    /* package */ InputStream realInputStream;

    private SeekablePumpStream pumpStream;

}
