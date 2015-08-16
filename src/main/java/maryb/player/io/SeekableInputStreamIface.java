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

import java.io.IOException;
import java.io.InputStream;

/**
 *
 * @author cy6erGn0m
 */
public abstract class SeekableInputStreamIface extends InputStream {

    public abstract int seek( int pos ) throws IOException;
}
