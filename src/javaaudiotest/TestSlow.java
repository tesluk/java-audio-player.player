package javaaudiotest;

import maryb.player.Player;

/**
 * @author Sergey Mashkov
 */
public class TestSlow {
    public static void main(String[] args) throws Exception {
        Player player = new Player();
        player.setSourceLocation("test_mp3/slow-sample.mp3");
        player.play();
        
        Thread.sleep(2000);

        player.stopSync();
    }
}
