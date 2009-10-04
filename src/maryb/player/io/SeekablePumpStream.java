/**
 *
 * (c) Sergey Mashkov (aka cy6erGn0m), 2009
 *
 * License: GNU LGPL v3
 * To read license read lgpl-3.0.txt from root of repository or follow URL:
 *      http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 */

package maryb.player.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

/**
 *
 * @author cy6erGn0m
 */
public class SeekablePumpStream {

    private final ByteBuffer bb = new ByteBuffer();

    private final Object osync = new Object();

    private volatile int wasReadFromSource = 0;

    private volatile boolean eof = false;

    private final InputStream rawStream;

    private final BufferedInputStream in;

    public SeekablePumpStream( InputStream rawStream ) {
        this.rawStream = rawStream;
        this.in = new BufferedInputStream( rawStream, 2048 );
    }

    public SeekableInputStreamIface openStream() {
        return new SeekableStream();
    }

    public SeekableInputStreamIface openStream( int pos ) throws IOException {
        return new SeekableStream( pos );
    }

    private class SeekableStream extends SeekableInputStreamIface {

        private volatile int currentPosition = 0;

        public SeekableStream() {
        }

        public SeekableStream( int pos ) throws IOException {
            seek( pos );
        }

        @Override
        public int read() throws IOException {
            if( eof ) {
                if( wasReadFromSource == currentPosition )
                    return -1;
                return bb.getBackBuffer()[currentPosition++];
            }

            try {
                synchronized( osync ) {
                    while( wasReadFromSource == currentPosition && !eof )
                        osync.wait();
                }
            } catch( InterruptedException ex ) {
                throw new InterruptedIOException();
            }

            if( eof && wasReadFromSource == currentPosition )
                return -1;

            return bb.getBackBuffer()[currentPosition++];
        }

        @Override
        public int read( byte[] b, int off, int len ) throws IOException {
            if( wasReadFromSource == currentPosition ) {
                if( eof )
                    return -1;

                try {
                    synchronized( osync ) {
                        while( wasReadFromSource == currentPosition && !eof ) {
                            osync.wait();
                        }
                    }
                } catch( InterruptedException e ) {
                    if( wasReadFromSource == currentPosition ) {
                        if( eof )
                            return 0;
                        throw new InterruptedIOException();
                    }
                }
            }

            int wasRead = Math.min( wasReadFromSource - currentPosition, len );
            if( wasRead > bb.getSize() - currentPosition )
                wasRead = bb.getSize() - currentPosition;

            if( wasRead <= 0 ) {
                if( eof )
                    return -1;
                return 0;
            }

            try {
                System.arraycopy( bb.getBackBuffer(), currentPosition, b, off, wasRead );
            } catch( ArrayIndexOutOfBoundsException e ) {
                // this possible if realClose was called between wasRead calculation
                // and array copy method call
                if( wasRead > bb.getSize() - currentPosition )
                    return 0;
                throw e;
            }
            currentPosition += wasRead;

            return wasRead;

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

            while( wasSkipped < n ) {
                int toBeRead = (int) Math.min( ( n - wasSkipped ), tmpBuffer.length );
                int wasRead = read( tmpBuffer, 0, toBeRead );
                if( wasRead < 0 )
                    break;
                wasSkipped += wasRead;
            }
            return wasSkipped;
        }

        public int seek( int pos ) throws IOException {
            if( pos >= wasReadFromSource )
                skip( pos - currentPosition );
            return ( currentPosition = pos );
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public int available() throws IOException {
            synchronized( osync ) {
                return wasReadFromSource - currentPosition;
            }
        }

    }

    private void eof() {
        eof = true;
        synchronized( osync ) {
            osync.notifyAll();
        }
        try {
            if( in != null )
                in.close();
        } catch( IOException ignore ) {
        }
    }

    public void pumpLoop() {
        byte[] buffer = new byte[1024];

        while( !eof && !closed ) {
            int wasRead = 0;
            try {
                wasRead = in.read( buffer );
                if( wasRead == -1 ) {
                    eof();
                }
            } catch( IOException e ) {
                if( !"Stream closed".equals( e.getMessage() ) )
                    e.printStackTrace();
                eof();
            }
            if( wasRead > 0 ) {
                bb.write( buffer, 0, wasRead );
                wasReadFromSource += wasRead;
                synchronized( osync ) {
                    osync.notifyAll();
                }
            }
        }
    }

    private volatile boolean closed = false;

    public void realClose() throws IOException {
        eof = true;
        closed = true;
        wasReadFromSource = 0;
        bb.reset();

        in.close();

        synchronized(osync) {
            osync.notifyAll();
        }
    }

    public boolean isAllDownloaded() {
        return eof;
    }

}
