/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package javaaudiotest.player;

import maryb.player.io.SeekablePumpStream;
import maryb.player.io.SeekableInputStreamIface;
import java.io.ByteArrayInputStream;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author cy6erGn0m
 */
public class SeekablePumpStreamTest {

    public SeekablePumpStreamTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void test1() throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream( new byte[]{ (byte) 1, (byte) 2, (byte) 3, (byte) 4 } );
        SeekablePumpStream sp = new SeekablePumpStream( bais );

        sp.pumpLoop();

        SeekableInputStreamIface sis = sp.openStream();

        sis.read();
        sis.read();

        if( sis.seek( 1 ) != 1 )
            fail( "seek failed" );

        byte[] bb = new byte[2];
        if( sis.read( bb ) != 2 )
            fail( "less than two bytes was read after seek" );

        if( sis.read() == -1 )
            fail( "unexpected EOF" );
        if( sis.read() != -1 )
            fail( "EOF expected" );

        if( bb[0] != 2 )
            fail( "fist byte should be 2" );

        if( bb[1] != 3 )
            fail( "second byte should be 3" );

    }
}
