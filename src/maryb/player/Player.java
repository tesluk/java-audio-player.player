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

    /**
     * Retrieves current volume level.
     * @return volume value in range between 0 and 1.
     */
    public float getCurrentVolume() {
        return currentVolume;
    }

    /**
     * Updates current volume. If playback is running, updates sound line
     * @param currentVolume volume value in range 0 and 1.
     * @throws IllegalArgumentException in case when requested volume is not
     * in expected range
     */
    public void setCurrentVolume( float currentVolume ) {
        if( currentVolume > 1. || currentVolume < 0 )
            throw new IllegalArgumentException( "volume should be non-negative and less or equal to 1.0" );
        this.currentVolume = currentVolume;

        SourceDataLine line = currentDataLine;
        if( line != null )
            populateVolume( line );
    }

    /**
     * Returns current source location
     * @return location
     */
    public String getSourceLocation() {
        return sourceLocation;
    }

    /**
     * Sets current source location.
     * It can contain URL or path to local file.
     * If playback is running, stop() is pending. All buffering operations
     * will be interrupted.
     * @param sourceLocation URL or path to local file
     */
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

    /**
     * Returns current player state
     * @return
     */
    public PlayerState getState() {
        return state;
    }

    /* package */ void setState( PlayerState state ) {
        this.state = state;
    }

    /**
     * Returns size of buffered part of media. Size is represented by time in
     * microseconds.
     * @return how many microseconds was buffered
     */
    public long getCurrentBufferedTimeMcsec() {
        return currentBufferedTimeMcsec;
    }

    /**
     * Returns current play position
     * @return how many microseconds was played
     */
    public long getCurrentPosition() {
        return currentPlayTimeMcsec + currentSeekPositionMcsec;
    }

    /**
     * Returns total size of playable media. Please note that this value is not
     * updated before playback started.
     * @return total media length in microseconds
     */
    public long getTotalPlayTimeMcsec() {
        return totalPlayTimeMcsec;
    }

    /**
     * Set pointer to player events listener
     * @param listener
     */
    public void setListener( PlayerEventListener listener ) {
        this.listener = listener;
    }

    /**
     * Returns current player listener
     * @return
     */
    public PlayerEventListener getListener() {
        return listener;
    }

    /**
     * Checks, is last media was played and end of media was reached.
     * @return
     */
    public boolean isEndOfMediaReached() {
        return endOfMediaReached;
    }
    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // internal data

    /* package */ final Object osync = new Object();

    /* package */ final Object seekSync = new Object();

    /* package */ volatile SourceDataLine currentDataLine;

    /* package */ final AtomicReference<PlayerThread> currentPlayerThread = new AtomicReference<PlayerThread>();

    /* package */ SeekThread seekThread = null;

    /* package */ long currentSeekTo = -1L;

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
        try {
            if( line != null && line.isControlSupported( FloatControl.Type.VOLUME ) ) {
                FloatControl c = (FloatControl) line.getControl( FloatControl.Type.VOLUME );
                float interval = c.getMaximum() - c.getMinimum();
                float cv = currentVolume;

                interval *= cv;
                c.setValue( c.getMinimum() + interval );
            }
            return;
        } catch(Throwable t) {
        }
        try {
            if( line != null && line.isControlSupported( FloatControl.Type.MASTER_GAIN ) ) {
                FloatControl c = (FloatControl) line.getControl( FloatControl.Type.MASTER_GAIN );
                float interval = c.getMaximum() - c.getMinimum();
                float cv = currentVolume;

                interval *= cv;
                c.setValue( c.getMinimum() + interval );
            }
            return;
        } catch(Throwable t) {
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

    /**
     * Attemts to start playback. If playback already running this method has no
     * any effect. Playback startup is async operation: method initiates
     * playback preparation and returns immediate - playback may run later.
     */
    public void play() {
        if( state == PlayerState.PLAYING )
            return;
        PlayerThread th = currentPlayerThread.get();

        if( th == null ) {
            th = new PlayerThread( this );
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

    /* package */ void setStateNotify( PlayerState pst ) {
        synchronized( osync ) {
            state = pst;
            osync.notifyAll();
        }
    }

    /**
     * Initiate playback preparation and wait until playback will start.
     * @throws InterruptedException
     */
    public void playSync() throws InterruptedException {
        play();
        waitForState( PlayerState.PLAYING );
    }

    private void interruptPlayback( PlayerState st ) {
        if( state == st )
            return;

        PlayerThread th = currentPlayerThread.get();
        if( th != null ) {
            if( th.isAlive() )
                th.die( st );
            else if( currentPlayerThread.compareAndSet( th, null ) )
                setStateNotify( st );
        } else
            setStateNotify( st );

    }

    /**
     * Requests playback stop. Method initiate shutdown and return immediate.
     * Playback stops later.
     */
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

    /**
     * Initiates playback stop and wait util it stops.
     * @throws InterruptedException
     */
    public void stopSync() throws InterruptedException {
        stop();
        waitForState( PlayerState.STOPPED );
    }

    /**
     * Requests pause. Returns immediate.
     */
    public void pause() {
        interruptPlayback( PlayerState.PAUSED );
    }

    /**
     * Requests pause and wait until playback pauses.
     * @throws InterruptedException
     */
    public void pauseSync() throws InterruptedException {
        pause();
        waitForState( PlayerState.PAUSED );
    }

    /**
     * Requests seek. Returns immediate. Seek will happens later.
     * @param newPos
     */
    public void seek( long newPos ) {
        synchronized( seekSync ) {
            currentSeekTo = newPos;
            if( seekThread == null )
                ( seekThread = new SeekThread( this ) ).start();
        }
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
