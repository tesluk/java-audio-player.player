package javaaudiotest;

import javazoom.jl.player.Player;

import java.io.FileInputStream;
import java.io.InputStream;

/**
 * @author Sergey Mashkov
 */
public class DirectPlayer {
    public static void main(String[] args) throws Exception {
        InputStream is = new FileInputStream("test_mp3/slow-sample.mp3");
        try {
            Player player = new Player(is);
            player.play();

            while (!player.isComplete())
                Thread.sleep(1500);

        } finally {
            is.close();
        }
    }
}
