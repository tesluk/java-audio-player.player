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

import javax.sound.sampled.AudioFormat;

/**
 *
 * @author cy6ergn0m
 */
public interface AbstractDecoder {

    AudioFormat getAudioFormat();

    void prepareDecode();

    short[] decodePortion();

    long evaluateTotalTime( long streamLength );

}
