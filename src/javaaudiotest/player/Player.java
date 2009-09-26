package javaaudiotest.player;

import javaaudiotest.player.io.SeekablePumpStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javaaudiotest.player.io.EmulateSlowInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/**
 *
 * @author cy6erGn0m
 */
public final class Player {

    // public-managed values
    private float currentVolume = 0.5f;

    private String sourceLocation = null;

    private volatile PlayerState state = PlayerState.STOPPED;

    private volatile long currentBufferedTimeMcsec = 0;

    private volatile long currentPlayTimeMcsec = 0;

    private volatile long currentSeekPositionMcsec = 0;

    private volatile long totalPlayTimeMcsec = 0;

    private PlayerEventListener listener;

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
            pump = pumpStream;
            is = realInputStream;

            pumpStream = null;
            is = null;
        }

        close( pump );
        close( is );
    }

    public PlayerState getState() {
        return state;
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

    public LineListener getLineListener() {
        return lineListener;
    }

    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // internal data

    private final Object osync = new Object();

    private Decoder decoder;

    private volatile SourceDataLine currentDataLine;

    private final AtomicReference<PlayerThread> currentPlayerThread = new AtomicReference<PlayerThread>();

    private final AtomicReference<PlayerHelperThread> currentPlayerHelperThread = new AtomicReference<PlayerHelperThread>();

    private AudioFormat getAudioFormatValue() {
        return new AudioFormat( decoder.getOutputFrequency(),
                16,
                decoder.getOutputChannels(),
                true,
                false );
    }

    private DataLine.Info getSourceLineInfo() {
        //DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt, 4000);
        DataLine.Info info = new DataLine.Info( SourceDataLine.class, getAudioFormatValue() );
        return info;
    }

    private SourceDataLine createLine() {
        Line line = null;

        try {
            line = AudioSystem.getLine( getSourceLineInfo() );
        } catch( LineUnavailableException ex ) {
            Logger.getLogger( Player.class.getName() ).log( Level.SEVERE, null, ex );
        }

        if( line == null || !( line instanceof SourceDataLine ) ) {
            return null; // TODO: handle problem
        }

        return (SourceDataLine) line;
    }

    private void flush() {
        final SourceDataLine l = currentDataLine;
        if( l != null ) {
            l.drain();
        }
    }

    private void populateVolume( SourceDataLine line ) {
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
            th = new PlayerThread( Integer.MAX_VALUE );
            if( currentPlayerThread.compareAndSet( null, th ) )
                th.start();
        }

        PlayerHelperThread hth = currentPlayerHelperThread.get();

        if( hth == null ) {
            hth = new PlayerHelperThread();
            if( currentPlayerHelperThread.compareAndSet( null, hth ) )
                hth.start();
        }
    }

    private void waitForState( PlayerState st ) throws InterruptedException {
        synchronized(osync) {
            while(state != st ) {
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
            th.requestedState = st;
            th.die();
        }

        PlayerHelperThread hth = currentPlayerHelperThread.get();
        if( hth != null )
            hth.die();
    }

    public void stop() {
        interruptPlayback( PlayerState.STOPPED );
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

    private LineListener lineListener = new LineListener() {

        public void update( LineEvent event ) {
            System.out.println( "line event: " + event );
            System.out.println( "line event type: " + event.getType() );
            System.out.println( "line event pos: " + event.getFramePosition() );
            System.out.println( "line event thread: " + Thread.currentThread().getName() );
        }

    };

    private InputStream realInputStream;

    private int realInputStreamLength;

    private SeekablePumpStream pumpStream;

    private class PlayerThread extends Thread {

        private volatile boolean dieRequested = false;

        private Bitstream bstream;

        private long mspos = 0;

        private int framesPlayed;

        private final int framesToBePlayed;

        private SourceDataLine line;

        private byte[] buff = new byte[8192];

        private InputStream stream;

        private PlayerState requestedState = null;

        public PlayerThread( int framesToBePlayed ) {
            super( "player-playback-thread" );
            this.framesToBePlayed = framesToBePlayed;
        }

        private void createOpenLine() throws LineUnavailableException {
            line = currentDataLine = createLine();
            line.open( getAudioFormatValue() );
            populateVolume( line );
            line.addLineListener( lineListener );
            line.start();

            synchronized( osync ) {
                state = PlayerState.PLAYING;
                osync.notifyAll();
            }
        }

        private boolean decodeFrame() throws BitstreamException, DecoderException, LineUnavailableException, InterruptedException {
            Header h = bstream.readFrame();
            if( h == null )
                return false;
            SampleBuffer sb = (SampleBuffer) decoder.decodeFrame( h, bstream );

            short[] samples = sb.getBuffer();
            int sz = samples.length << 1;
            if( sz > buff.length ) {
                int limit = buff.length + 1024;
                while( sz > limit )
                    limit += 1024;
                buff = new byte[limit];
            }

            int idx = 0;
            for( short s : samples ) {  // TODO: change it!!!!!
                buff[idx++] = (byte) s;
                buff[idx++] = (byte) ( s >> 8 );
            }

            if( line == null ) {
                createOpenLine();    // TODO: бяка!
                totalPlayTimeMcsec = (long) ( 1000. * h.total_ms( realInputStreamLength ) );
            }


            synchronized( osync ) {
                if( state == PlayerState.PAUSED_BUFFERING )
                    osync.wait();
                else if( state == PlayerState.STOPPED )
                    return false;

                int wasWritten = 0;
                while( wasWritten < idx && !dieRequested && line.isOpen() ) {
                    wasWritten += line.write( buff, 0, idx );
                }
            }

            framesPlayed++;
            //written pos in microseconds += (long) (1000.f * h.ms_per_frame());

            bstream.closeFrame();
            return true;
        }

        private void openInputStream() throws IOException {
            String location = sourceLocation;
            if( location == null )
                throw new IllegalArgumentException();

            if( location.startsWith( "http://" ) ) {
                //HttpURLConnection c =
                URLConnection c = new URL( location ).openConnection();
                c.connect();
                realInputStreamLength = c.getContentLength();
                realInputStream = c.getInputStream();
            } else {
                boolean slow = false;
                if( location.startsWith( "!" ) ) {
                    // debug case: slow input stream impl
                    location = location.substring( 1 );
                    slow = true;
                }
                File f = new File( location );
                if( f.exists() && !f.isDirectory() && f.canRead() )
                    realInputStream = new FileInputStream( f );
                else
                    throw new IllegalArgumentException( "Bad path to file" );
                realInputStreamLength = (int) f.length();
                if( slow )
                    realInputStream = new EmulateSlowInputStream( realInputStream, 0.1 );
            }
        }

        private void processID3v2() throws IOException {
            InputStream is = bstream.getRawID3v2();
            if( is != null ) {
                System.out.println( "ID3v2 found" );
                InputStreamReader isr = new InputStreamReader( is );
                OutputStreamWriter osw = new OutputStreamWriter( System.out );
                try {
                    char[] buffer = new char[128];
                    int wasRead;

                    while( ( wasRead = isr.read( buffer ) ) != -1 ) {
                        osw.write( buffer, 0, wasRead );
                    }
                    osw.flush();
                    System.out.println();
                    System.out.println( "end of ID3" );
                } finally {
                    close( isr );
                    close( is );
                }
            }
        }

        private void resumeFromPause() {
            synchronized( osync ) {
                if( state == PlayerState.PAUSED ) {
                    //currentSeekPositionMcsec = getCurrentPosition(); // TODO: is it actualy required?
                    //currentPlayTimeMcsec = 0;
                }
            }
        }

        private void skipFrames( long timeMcsec ) throws BitstreamException {
            long skipped = 0;
            Header h;

            while( !dieRequested && ( skipped < timeMcsec ) && ( ( h = bstream.readFrame() ) != null ) ) {
                skipped += (long) ( 1000. * h.ms_per_frame() );
                bstream.closeFrame();
            }
        }

        @Override
        public void run() {
            try {
                if( pumpStream == null ) {
                    openInputStream();
                    pumpStream = new SeekablePumpStream( realInputStream );

                    new Thread( "player-stream-pump-thread" ) {

                        @Override
                        public void run() {
                            pumpStream.pumpLoop();
                        }

                    }.start();
                    new PlayerBufferedResolverThread().start();
                }

                resumeFromPause();

                stream = pumpStream.openStream();
                bstream = new Bitstream( stream );
                processID3v2();

                decoder = new Decoder();

                line = currentDataLine = null;

                skipFrames( currentSeekPositionMcsec );

                while( !dieRequested && framesPlayed < framesToBePlayed ) {
                    if( !decodeFrame() )
                        break;
                }

                if( line != null ) {
                    line.flush();
                    line.drain();
                    currentSeekPositionMcsec += line.getMicrosecondPosition();
                    currentPlayTimeMcsec = 0;
                    System.out.println( "last play position: " + ( line.getMicrosecondPosition() / 1000 ) + " ms" );
                }

            } catch( BitstreamException e ) {
                e.printStackTrace();
            } catch( IOException e ) {
                e.printStackTrace();
            } catch( DecoderException e ) {
                e.printStackTrace();
            } catch( LineUnavailableException e ) {
                e.printStackTrace();
            } catch( InterruptedException e ) {
                e.printStackTrace();
            } finally {
                try {
                    bstream.close();
                } catch( BitstreamException ignore ) {
                }
                //close(pumpStream);
                //close( realInputStream );
                if( line != null ) {
                    mspos = line.getMicrosecondPosition() >> 32;
                    line.stop();
                    line.close();
                }

                if( currentPlayerThread.compareAndSet( this, null ) ) {
                    // TODO: notify stopped
                    synchronized(osync) {
                        if( requestedState != null ) {
                            switch( requestedState  ) {
                                case STOPPED:
                                    currentSeekPositionMcsec = 0;
                                    state = requestedState;
                                    break;
                                case PAUSED:
                                    state = requestedState;
                                    break;
                                default:
                                    state = PlayerState.STOPPED;
                                    break;
                            }

                        } else {
                            state = PlayerState.STOPPED;
                            currentSeekPositionMcsec = 0;
                        }
                        osync.notifyAll();
                    }
                }

            }
        }

        public void die() {
            dieRequested = true;
            interrupt();
        }

        public void setRequestedState( PlayerState requestedState ) {
            this.requestedState = requestedState;
        }

    }

    private class PlayerHelperThread extends Thread {

        public PlayerHelperThread() {
            super( "player-helper-thread" );
        }

        private volatile boolean dieRequested = false;

        @Override
        public void run() {
            try {
                SourceDataLine line;
                int available;
                int size;
                float rate;
                long time;
                while( !dieRequested && currentPlayerThread.get() != null ) {
                    sleep( 150 );
                    if( ( line = currentDataLine ) != null ) {
                        size = line.getBufferSize();
                        available = line.available();
                        time = line.getMicrosecondPosition();

                        currentPlayTimeMcsec = time;

                        rate = ( (float) available ) / ( (float) size );
                        synchronized( osync ) {
                            if( rate > 0.9 ) {
                                if( state == PlayerState.PLAYING && !pumpStream.isAllDownloaded() ) {
                                    System.out.println( "low speed detected.. lets pause" );
                                    state = PlayerState.PAUSED_BUFFERING;
                                    continue;
                                    //line.stop();   // !! NEVER STOP LINE to prevent player-playback-thread spinning on Linux
                                }
                            }
                            if( state == PlayerState.PAUSED_BUFFERING ) {
                                if( ( /*rate == 0. && */ ( ( currentBufferedTimeMcsec - currentPlayTimeMcsec ) > 10000000L ) ) || pumpStream.isAllDownloaded() ) {
                                    // NOTE: rate unchecked, because in this state player-playback-thread sleeping and can't fill device buffer
                                    System.out.println( "lets resume" );
                                    osync.wait( 500 );
                                    state = PlayerState.PLAYING;
                                    osync.notifyAll();
                                    osync.wait( 500 );
                                    //line.start();
                                }
                            }
                        }

                    }
                }
            } catch( InterruptedException ignore ) {
            } finally {
                currentPlayerHelperThread.compareAndSet( this, null );
            }
        }

        public void die() {
            dieRequested = true;
            interrupt();
        }

    }

    private class PlayerBufferedResolverThread extends Thread {

        public PlayerBufferedResolverThread() {
            super( "player-buffered-time-resolver-thread" );
        }

        private volatile boolean dieRequested = false;

        @Override
        public void run() {
            try {
                SeekablePumpStream s = pumpStream;
                if( s != null ) {
                    InputStream is = s.openStream();
                    Bitstream bs = new Bitstream( is );
                    currentBufferedTimeMcsec = 0;
                    double value = 0;

                    Header h;
                    while( !dieRequested && ( h = bs.readFrame() ) != null ) {
                        value = 1000. * h.ms_per_frame();

                        currentBufferedTimeMcsec += (long) value;
                        bs.closeFrame();
                    }
                }
            } catch( BitstreamException e ) {
                e.printStackTrace();
            }/* catch( InterruptedException ignore ) {
            }*/
        }

        public void die() {
            dieRequested = true;
            interrupt();
        }

    }

    private class EventNotificator extends Thread {

        private void callChanged() {
            PlayerEventListener l = listener;
            if( l != null ) {
                try {
                    l.stateChanged();
                } catch( Throwable t ) {
                    t.printStackTrace();
                }
            }
        }

        @Override
        public void run() {
            PlayerState lastState = state;
            boolean lastEOM = false;
            boolean letsFire = false;

            try {
                while(currentPlayerThread.get() != null) {
                    synchronized(osync) {
                        if( state == lastState ) {
                            osync.wait( 500 );
                        } else {
                            letsFire = true;
                            lastState = state;
                        }
                    }
                    // TOOD: notify here
                }
            } catch( InterruptedException ignore ) {
            }
        }

    }

}

