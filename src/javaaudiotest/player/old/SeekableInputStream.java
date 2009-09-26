package javaaudiotest.player.old;

import javaaudiotest.player.io.ByteBuffer;
import javaaudiotest.player.*;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 *
 * @author cy6erGn0m
 */
public class SeekableInputStream extends FilterInputStream {

    private final ByteBuffer bb = new ByteBuffer();
    private int currentPosition = 0;
    private int wasReadFromSource = 0;
    private final Object osync = new Object();

    public SeekableInputStream( InputStream in ) {
        super( in );
    }

    @Override
    public int read() throws IOException {
        if( wasReadFromSource == currentPosition ) {
            int r = super.read();
            if( r == -1 )
                return -1;

            bb.write( r );
            wasReadFromSource++;
            currentPosition++;

            return r;
        } else {
            if( bb.getSize() == 0 )
                return -1;

            currentPosition++;
            return bb.getBackBuffer()[(int) currentPosition];
        }
    }

    @Override
    public int read( byte[] b, int off, int len ) throws IOException {
        if( wasReadFromSource == currentPosition ) {
            int wasRead = super.read( b, off, len );
            if( wasRead < 0 )
                return -1;

            bb.write( b, off, wasRead );
            currentPosition += wasRead;
            wasReadFromSource += wasRead;

            return wasRead;
        } else {
            if( bb.getSize() < ( currentPosition + len ) ) {
                int wasRead = read( b, off, bb.getSize() - currentPosition );
                wasRead += read( b, off + wasRead, len - wasRead );
                return wasRead;
            }
            System.arraycopy( bb.getBackBuffer(), currentPosition, b, off, len );
            currentPosition += len;
            return len;
        }
    }

    @Override
    public int read( byte[] b ) throws IOException {
        return read( b, 0, b.length );
    }

    @Override
    public long skip( long n ) throws IOException {
        if( n < 0 )
            return 0;

        byte[] tmpBuffer = new byte[1024];
        long wasSkipped = 0;

        while ( wasSkipped < n ) {
            int toBeRead = (int) Math.min( ( n - wasSkipped ), tmpBuffer.length );
            int wasRead = read( tmpBuffer, 0, toBeRead );
            if( wasRead < 0 )
                break;
            wasSkipped += wasRead;
        }
        return wasSkipped;
    }

    public int seek( int pos ) throws IOException {
        if( pos > currentPosition ) {
            skip( pos - currentPosition );
        } else if( pos < currentPosition ) {
            currentPosition = pos;
        }
        return currentPosition;
    }

}
