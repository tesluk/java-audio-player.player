/**
 *
 * (c) Sergey Mashkov (aka cy6erGn0m), 2009
 *
 * License: GNU LGPL v3
 * To read license read lgpl-3.0.txt from root of repository or follow URL:
 *      http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 */
package maryb.player.decoder;

import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/**
 *
 * @author cy6ergn0m
 */
public class MP3Decoder implements AbstractDecoder {

    private final InputStream is;

    private boolean prepared = false;

    private final Decoder decoder;

    private final Bitstream bstream;

    private Header firstHeader;

    public MP3Decoder( InputStream is ) {
        this.is = is;

        this.bstream = new Bitstream( is );
        this.decoder = new Decoder();
    }

    public AudioFormat getAudioFormat() {
        if( !prepared )
            prepareDecode();

        return new AudioFormat( decoder.getOutputFrequency(),
                16,
                decoder.getOutputChannels(),
                true,
                ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN );
    }

    public void prepareDecode() {
        try {
            Header h = bstream.readFrame();
            decoder.decodeFrame( h, bstream );
            bstream.unreadFrame();
            firstHeader = h;

            prepared = true;
        } catch( DecoderException ex ) {
            Logger.getLogger( MP3Decoder.class.getName() ).log( Level.SEVERE, null, ex );
        } catch( BitstreamException ex ) {
            Logger.getLogger( MP3Decoder.class.getName() ).log( Level.SEVERE, null, ex );
        }
    }

    public short[] decodePortion() {
        SampleBuffer sb;
        try {
            Header h = bstream.readFrame();
            sb = (SampleBuffer) decoder.decodeFrame( h, bstream );
            bstream.closeFrame();

            return sb.getBuffer();
            // TODO: react errors
        } catch( DecoderException ex ) {
            Logger.getLogger( MP3Decoder.class.getName() ).log( Level.SEVERE, null, ex );
        } catch( BitstreamException ex ) {
            Logger.getLogger( MP3Decoder.class.getName() ).log( Level.SEVERE, null, ex );
        }
        return null;
    }

    public long evaluateTotalTime( long streamLength ) {
        if( !prepared )
            prepareDecode();

        // TODO: process long value by Integer.MAX_VALUE portions
        return (long) ( 1000. * firstHeader.total_ms( (int) streamLength ) );
    }

}
