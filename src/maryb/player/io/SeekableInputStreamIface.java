package maryb.player.io;

import java.io.IOException;
import java.io.InputStream;

/**
 *
 * @author cy6erGn0m
 */
public abstract class SeekableInputStreamIface extends InputStream {

    public abstract int seek( int pos ) throws IOException;
}
