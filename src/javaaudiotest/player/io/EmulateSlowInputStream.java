package javaaudiotest.player.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Random;

/**
 *
 * @author cy6erGn0m
 */
public class EmulateSlowInputStream extends FilterInputStream {

    private final double msPerByte;

    private final Random rnd = new Random( ~System.currentTimeMillis() );

    public EmulateSlowInputStream( InputStream in, double msPerByte ) {
        super( in );
        this.msPerByte = msPerByte;
    }

    private void sleep( int mult ) throws IOException {
        try {
            if( msPerByte > 0 ) {
                long timeToSleep = (long) (msPerByte * mult * rnd.nextFloat());
                if( timeToSleep > 15000 )
                    timeToSleep = 15000;
                Thread.sleep( timeToSleep );
            }
        } catch ( InterruptedException ex ) {
            throw new InterruptedIOException();
        }
    }

    @Override
    public int read() throws IOException {
        sleep(1);
        return super.read();
    }

    @Override
    public int read( byte[] b, int off, int len ) throws IOException {
        sleep(len);
        return super.read( b, off, len );
    }

    @Override
    public int read( byte[] b ) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public long skip( long n ) throws IOException {
        sleep( (int)n >> 1 );
        return super.skip( n );
    }
}
