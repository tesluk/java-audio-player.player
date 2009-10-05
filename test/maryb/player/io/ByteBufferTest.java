/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package maryb.player.io;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author cy6ergn0m
 */
public class ByteBufferTest {

    public ByteBufferTest() {
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
    public void testWrite_3args() {
        ByteBuffer bb = new ByteBuffer();
        bb.write( new byte[] {1, 2, 3}, 0, 3 );

        if( bb.getSize() != 3 )
            fail( "byte buffer has invalid length" );

        byte[] back = bb.getBackBuffer();
        if( back[0] != 1 || back[1] != 2 || back[2] != 3 )
            fail( "back buffer contains invalid data" );
    }

    @Test
    public void testWrite_3args_2() {
        ByteBuffer bb = new ByteBuffer();
        bb.write( new byte[] {1, 2, 3}, 1, 1 );

        if( bb.getSize() != 1 )
            fail( "byte buffer has invalid length" );

        byte[] back = bb.getBackBuffer();
        if( back[0] != 2 )
            fail( "back buffer contains invalid data" );
    }

    @Test
    public void testWrite_3args_3() {
        ByteBuffer bb = new ByteBuffer();
        bb.write( new byte[] {1, 2, 3}, 1, 0 );

        if( bb.getSize() != 0 )
            fail( "byte buffer has invalid length" );

    }

    @Test
    public void testWrite_int() {
        ByteBuffer bb = new ByteBuffer();
        bb.write( 0x07 );

        if( bb.getSize() != 1 )
            fail( "byte buffer does not contain" );

        byte[] back = bb.getBackBuffer();
        if( back[0] != 0x07 )
            fail( "back buffer contains invalid data" );
    }

    @Test
    public void testGetSize() {
        ByteBuffer bb = new ByteBuffer();
        bb.write( 0x07 );

        if( bb.getSize() != 1 )
            fail( "size is invalid" );
    }

    @Test
    public void testReset() {
        ByteBuffer bb = new ByteBuffer();
        bb.write( 0x07 );

        bb.reset();

        if( bb.getSize() != 0 )
            fail( "byte buffer did not reset" );
    }

}