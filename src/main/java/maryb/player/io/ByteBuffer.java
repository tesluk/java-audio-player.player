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

/**
 * Simple byte buffer implementation. Concurrent-safe.
 * @author cy6erGn0m
 */
public class ByteBuffer {

    private byte[] data = new byte[65535];
    private int idx = 0;
    private final Object osync = new Object();

    private void growBuffer( int length ) {
        int limit = data.length;

        while ( length >= limit )
            limit <<= 1;

        if( limit != data.length ) {
            byte[] newData = new byte[limit];
            System.arraycopy( data, 0, newData, 0, idx );
            data = newData;
        }
    }

    public void write( byte[] buffer, int offset, int len ) {
        if( len < 0 )
            throw new IllegalArgumentException();

        if( len == 0 )
            return;

        synchronized( osync ) {
            growBuffer( idx + len );
            System.arraycopy( buffer, offset, data, idx, len );
            idx += len;
        }
    }

    public void write( int b ) {
        synchronized( osync ) {
            growBuffer( idx + 1 );
            data[idx++] = (byte) b;
        }
    }

    public byte[] getBackBuffer() {
        return data;
    }

    public int getSize() {
        return idx;
    }

    public void reset() {
        synchronized( osync ) {
            idx = 0;
            data = null;
            data = new byte[256];
        }
    }
}
