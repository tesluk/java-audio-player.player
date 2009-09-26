/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package javaaudiotest;

import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.Player;

/**
 *
 * @author cy6erGn0m
 */
public class Main {

    private static volatile boolean done = false;

    /**
     * @param args the command line arguments
     */
    public static void main( String[] args ) throws Exception {
//        AudioInputStream ais = AudioSystem.getAudioInputStream( new URL("file:///C:\\Users\\cy6erGn0m\\Music\\11_Part1.wav" ));
//
//
//        SourceDataLine sdl = AudioSystem.getSourceDataLine( ais.getFormat() );
//        ais.skip( ais.getFrameLength() );
//
//        sdl.addLineListener( new LineListener() {
//
//            public void update( LineEvent event ) {
//                System.out.println( "event: " + event.toString() );
//            }
//
//        });
//
//        Control[] ctrls = sdl.getControls();
//
//        sdl.start();
//
////        sdl.drain();

//        for( Mixer.Info m : AudioSystem.getMixerInfo() ) {
//            System.out.println( m );
//        }
        URL url = new URL( "file:///C:\\Users\\cy6erGn0m\\Music\\Chicane - Turning Corners.mp3" );

        final AudioDevice dev = FactoryRegistry.systemRegistry().createAudioDevice();
        final Player player = new Player( url.openStream(), dev );

        System.out.println( "audio device is " + dev + ", class = " + dev.getClass().getName() );
        
        new Thread() {

            @Override
            public void run() {
                try {
                    super.run();
                    player.play();
                } catch ( JavaLayerException ex ) {
                    Logger.getLogger( Main.class.getName() ).log( Level.SEVERE, null, ex );
                } finally {
                    done = true;
                }
            }
        }.start();

        new Thread() {

            @Override
            public void run() {
                super.run();
                try {
                    while ( !done ) {
                        System.out.println( Integer.toString(player.getPosition() / 1000) + " s played" );
                        sleep( 3000 );
                    }
                } catch ( InterruptedException ex ) {
                    Logger.getLogger( Main.class.getName() ).log( Level.SEVERE, null, ex );
                }
            }
        }.start();
    }
}
