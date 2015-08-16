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

import javazoom.jl.decoder.*;
import maryb.player.io.EmulateSlowInputStream;
import maryb.player.io.SeekablePumpStream;

import javax.sound.sampled.*;
import javax.sound.sampled.Line.Info;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author cy6ergn0m
 */
/* package */ class PlayerThread extends Thread {

    private static final Logger LOG = Logger.getLogger(PlayerThread.class.getName());
    
    private final Player parent;

    private volatile boolean dieRequested = false;

    private Bitstream bstream;

    private SourceDataLine line;

    private ByteBuffer bb = ByteBuffer.allocate(88200);

    private InputStream stream;

    private PlayerState requestedState = null;

    private Decoder decoder;

    private AudioFormat getAudioFormatValue() {
        return new AudioFormat(decoder.getOutputFrequency(),
                16,
                decoder.getOutputChannels(),
                true,
                false);

//        LOG.info("File info: bs = " + decoder.getOutputBlockSize() + ", channels =" + decoder.getOutputChannels() + ", freq = " + decoder.getOutputFrequency());
        
//        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
//                decoder.getOutputFrequency(),
//                16,
//                decoder.getOutputChannels(),
//                decoder.getOutputChannels() * 2,
//                decoder.getOutputFrequency(),
//                false
//                );
    }

    public PlayerThread(Player parent) {
        super("player-playback-thread");
        setPriority(MAX_PRIORITY);
        this.parent = parent;
    }

    private void createOpenLine() throws LineUnavailableException {
        line = parent.currentDataLine = createLine();
        line.open(getAudioFormatValue(), 88200);
        LOG.info("Line opened for the format " + line.getFormat());

        parent.populateVolume(line);
        line.start();
    }

    private boolean decodeFrame() throws BitstreamException, DecoderException, LineUnavailableException, InterruptedException {
        Header h;
        SampleBuffer sb;

        bb.clear();

        float buffered = 0.f;
        do {
            h = bstream.readFrame();
            if (h == null)
                break;
            sb = (SampleBuffer) decoder.decodeFrame(h, bstream);
            if (line == null) {
                createOpenLine();    // TODO: бяка!
                parent.totalPlayTimeMcsec = (long) (1000. * h.total_ms(parent.realInputStreamLength));
                LOG.log(Level.FINE, "fq: " + decoder.getOutputFrequency());
                LOG.log(Level.FINE, "sr: " + line.getFormat().getSampleRate());

                bb.order(line.getFormat().isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
            }

            short[] samples = sb.getBuffer();
            int sz = sb.getBufferLength() * 2;

            if (sz > bb.remaining()) {
                int olsSize = bb.position() + sz;
                int limit = bb.capacity() + 1024;
                while (olsSize > limit)
                    limit += 1024;
                ByteBuffer newBb = ByteBuffer.allocate(limit);
                newBb.clear();
                bb.flip();
                newBb.put(bb);
                bb = newBb;
                bb.order(line.getFormat().isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
            }

            for (int idx = 0, m = sb.getBufferLength(); idx < m; ++idx)
                bb.putShort(samples[idx]);

            buffered += h.ms_per_frame();
            bstream.closeFrame();
        } while (buffered < 200.f);

        if (bb.position() == 0)
            return false;

        synchronized (parent.osync) {
            while (parent.getState() == PlayerState.PAUSED_BUFFERING)
                parent.osync.wait();
            if (parent.getState() == PlayerState.STOPPED)
                return !dieRequested;
        }

        int wasWritten;
        bb.flip();
        while (bb.remaining() > 0 && !dieRequested && line.isOpen() && parent.getState() != PlayerState.STOPPED) {
            if ((wasWritten = line.write(bb.array(), 0, bb.remaining())) == -1)
                break;
            bb.position(bb.position() + wasWritten);
        }

        return true;
    }

    private void openInputStream() throws IOException {
        String location = parent.getSourceLocation();
        if (location == null)
            throw new IllegalArgumentException();

        if (location.startsWith("http://")) {
            HttpURLConnection.setFollowRedirects(true);
            URLConnection c = new URL(location).openConnection();
            c.connect();

            parent.realInputStreamLength = c.getContentLength();
            parent.realInputStream = c.getInputStream();
        } else {
            boolean slow = false;
            if (location.startsWith("!")) {
                // debug case: slow input stream impl
                location = location.substring(1);
                slow = true;
            }
            File f = new File(location);
            if (f.exists() && !f.isDirectory() && f.canRead())
                parent.realInputStream = new FileInputStream(f);
            else
                throw new IllegalArgumentException("Bad path to file: '" + location + "'");
            parent.realInputStreamLength = (int) f.length();
            if (slow)
                parent.realInputStream = new EmulateSlowInputStream(parent.realInputStream, 0.3);
        }
    }

    private void processID3v2() throws IOException {
//        InputStream is = bstream.getRawID3v2();
//        if( is != null ) {
//            System.out.println( "ID3v2 found" );
//            InputStreamReader isr = new InputStreamReader( is );
//            OutputStreamWriter osw = new OutputStreamWriter( System.out );
//            try {
//                char[] buffer = new char[128];
//                int wasRead;
//
//                while( ( wasRead = isr.read( buffer ) ) != -1 ) {
//                    //osw.write( buffer, 0, wasRead );
//                    }
//                osw.flush();
//                System.out.println();
//                System.out.println( "end of ID3" );
//            } finally {
//                parent.close( isr );
//                close( is );
//            }
//        }
    }

    private SourceDataLine createLine() {
        Line newLine = null;

        try {
            newLine = AudioSystem.getLine(getSourceLineInfo());
        } catch (LineUnavailableException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }

        if (newLine == null || !(newLine instanceof SourceDataLine)) {
            return null; // TODO: handle problem
        }

        return (SourceDataLine) newLine;
    }

    private Line.Info getSourceLineInfo() {
        //DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt, 4000);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, getAudioFormatValue());
        Info[] infos = AudioSystem.getSourceLineInfo(info);
        if (infos.length == 0) {
            LOG.warning("There are no suitable lines for format " + getAudioFormatValue());
            return null;
        }

        Info selected = infos[0];
        LOG.info("Selected channel: " + selected.toString());

        return selected;
    }

    private void skipFrames(long timeMcsec) throws BitstreamException {
        long skipped = 0;
        Header h;

        while (!dieRequested && (skipped < timeMcsec) && ((h = bstream.readFrame()) != null)) {
            skipped += (long) (1000. * h.ms_per_frame());
            bstream.closeFrame();
        }
    }

    private void stopAndCloseLine() {
        try {
            line.stop();
        } catch (Throwable ignore) {
        }
        try {
            line.close();
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void run() {
        try {
            if (parent.getPumpStream() == null) {
                openInputStream();
                parent.setPumpStream(new SeekablePumpStream(parent.realInputStream));

                new Thread("player-stream-pump-thread") {

                    @Override
                    public void run() {
                        parent.getPumpStream().pumpLoop();
                        synchronized (parent.osync) {
                            parent.osync.notifyAll();
                        }
                    }

                }.start();
                new PlayerBufferedResolverThread(parent).start();
            } else {
            }

            parent.endOfMediaReached = false;

            stream = parent.getPumpStream().openStream();
            bstream = new Bitstream(stream);
            processID3v2();

            decoder = new Decoder();

            line = parent.currentDataLine = null;

            synchronized (parent.seekSync) {
                while (parent.seekThread != null)
                    parent.seekSync.wait();
            }

            synchronized (parent.osync) {
                parent.setState(PlayerState.PLAYING);
                parent.osync.notifyAll();
            }
            new PlayerListenerNotificatorThread(parent, this).start();
            new PlayerHelperThread(parent, this).start();

            skipFrames(parent.currentSeekPositionMcsec);

            long lastUpdate = 0;

            while (!dieRequested) {
                if (!decodeFrame())
                    break;

                parent.currentPlayTimeMcsec = line.getMicrosecondPosition();

                long now = System.currentTimeMillis();
                parent.lastPlayerActivity = now;
                if (now - lastUpdate > 250) {
                    synchronized (parent.osync) {
                        parent.osync.notifyAll();
                    }
                    lastUpdate = now;
                }

            }

            if (line != null) {
                line.flush();
                line.drain();
                parent.currentSeekPositionMcsec += line.getMicrosecondPosition();
                parent.currentPlayTimeMcsec = 0;
                parent.endOfMediaReached = !dieRequested;
            }

        } catch (BitstreamException e) {
            e.printStackTrace();
        } catch (InterruptedIOException ignore) {
        } catch (IOException e) {
            e.printStackTrace();
        } catch (DecoderException e) {
            e.printStackTrace();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        } catch (InterruptedException ignore) {
        } finally {
            try {
                if (bstream != null)
                    bstream.close();
            } catch (BitstreamException ignore) {
            } catch (Throwable t) {
                t.printStackTrace();
            }

            if (line != null) {
                stopAndCloseLine();
                line = null;
                parent.currentDataLine = null;
            }

            if (parent.currentPlayerThread.compareAndSet(this, null)) {
                // TODO: notify stopped
                synchronized (parent.osync) {
                    if (requestedState != null) {
                        switch (requestedState) {
                            case STOPPED:
                                parent.currentSeekPositionMcsec = 0;
                                parent.setState(requestedState);
                                break;
                            case PAUSED:
                                parent.setState(requestedState);
                                break;
                            default:
                                parent.setState(PlayerState.STOPPED);
                                break;
                        }

                    } else {
                        parent.currentSeekPositionMcsec = 0;
                        parent.currentPlayTimeMcsec = 0;
                        parent.setState(PlayerState.STOPPED);
                    }
                    parent.osync.notifyAll();
                }
            } else {
                synchronized (parent.osync) {
                    parent.osync.notifyAll();
                }
            }

        }
    }

    public void die(PlayerState requestedState) {
        if (requestedState != PlayerState.STOPPED && requestedState != PlayerState.PAUSED)
            throw new IllegalArgumentException("player thread may die only with stopped or paused state");
        this.requestedState = requestedState;
        dieRequested = true;
        interrupt();
    }

    public void die() {
        die(PlayerState.STOPPED);
    }

}
