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

import java.io.InputStream;
import maryb.player.io.SeekablePumpStream;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Header;

/**
 *
 * @author cy6ergn0m
 */
/* package */ class PlayerBufferedResolverThread extends Thread {

    private final Player parent;


    public PlayerBufferedResolverThread(Player parent) {
        super( "player-buffered-time-resolver-thread" );
        this.parent = parent;
    }

    private volatile boolean dieRequested = false;

    @Override
    public void run() {
        try {
            SeekablePumpStream s = parent.getPumpStream();
            if( s != null ) {
                InputStream is = s.openStream();
                Bitstream bs = new Bitstream( is );
                parent.currentBufferedTimeMcsec = 0;
                double value = 0;
                long lastUpdateTime = 0;

                Header h;
                while( !dieRequested && ( h = bs.readFrame() ) != null ) {
                    value = 1000. * h.ms_per_frame();

                    parent.currentBufferedTimeMcsec += (long) value;

                    long now = System.currentTimeMillis();
                    if( now - lastUpdateTime > 800 ) {
                        lastUpdateTime = now;
                        synchronized( parent.osync ) {
                            parent.osync.notifyAll();
                        }
                    }

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
