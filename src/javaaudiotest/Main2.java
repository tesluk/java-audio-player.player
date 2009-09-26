/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package javaaudiotest;

import java.io.FileInputStream;
import javaaudiotest.player.Player;

/**
 *
 * @author cy6erGn0m
 */
public class Main2 {
    public static void main( String[] args ) throws Exception {
        Player p = new Player();
        p.setSourceLocation( "C:\\Users\\cy6erGn0m\\Music\\Chicane - Turning Corners.mp3" );
        //p.debugPlayStreamStart( new FileInputStream( "C:\\Users\\cy6erGn0m\\Music\\Chicane - Turning Corners.mp3") );

        Thread.sleep( 6000 );
        p.setCurrentVolume( 0.8f );
    }
}
