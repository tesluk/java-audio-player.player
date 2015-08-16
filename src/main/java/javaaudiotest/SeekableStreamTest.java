/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * SeekableStreamTest.java
 *
 * Created on 24.09.2009, 0:48:59
 */
package javaaudiotest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Random;
import maryb.player.io.SeekablePumpStream;
import javax.swing.SwingUtilities;

/**
 *
 * @author cy6erGn0m
 */
public class SeekableStreamTest extends javax.swing.JFrame {

    private volatile int youMayRead = 0;
    private final int theMaximum = 15000;
    private final Object osync = new Object();
    private final Random rnd = new Random( ~System.currentTimeMillis() );
    private volatile int wasReadOutside = 0;
    private final InputStream garbageStream = new InputStream() {

        private int wasRead = 0;

        @Override
        public int read() throws IOException {
            try {
                synchronized( osync ) {
                    while ( true ) {
                        if( wasRead < youMayRead ) {
                            wasRead++;
                            return rnd.nextInt( 255 );
                        }
                        if( wasRead == theMaximum )
                            return -1;
                        osync.wait();
                    }
                }
            } catch ( InterruptedException e ) {
                throw new InterruptedIOException();
            }
        }
    };
    private final SeekablePumpStream stream = new SeekablePumpStream( garbageStream );

    /** Creates new form SeekableStreamTest */
    public SeekableStreamTest() {
        initComponents();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jButton1 = new javax.swing.JButton();
        jProgressBar1 = new javax.swing.JProgressBar();
        jButton2 = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jButton1.setText("was read few bytes");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jProgressBar1.setMaximum(15000);

        jButton2.setText("Start test");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jButton2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 174, Short.MAX_VALUE)
                        .addComponent(jButton1))
                    .addComponent(jProgressBar1, javax.swing.GroupLayout.DEFAULT_SIZE, 380, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton2)
                    .addComponent(jButton1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jProgressBar1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        jButton2.setEnabled( false );

        new Thread() {

            @Override
            public void run() {
                stream.pumpLoop();
            }
        }.start();

        new Thread() {

            @Override
            public void run() {
                byte[] data = new byte[16];
                InputStream is = stream.openStream();
                try {
                    while ( true ) {
                        final int wasRead = is.read( data );
                        if( wasRead < 0 )
                            break;

                        wasReadOutside += wasRead;

                        SwingUtilities.invokeLater( new Runnable() {

                            public void run() {
                                jProgressBar1.setValue( wasReadOutside );
                            }
                        } );
                    }
                } catch ( IOException e ) {
                    e.printStackTrace();
                }
            }
        }.start();
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        synchronized( osync ) {
            youMayRead += rnd.nextInt( 1400 );
            if( youMayRead > theMaximum )
                youMayRead = theMaximum;

            osync.notifyAll();
        }
    }//GEN-LAST:event_jButton1ActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main( String args[] ) {
        java.awt.EventQueue.invokeLater( new Runnable() {

            public void run() {
                new SeekableStreamTest().setVisible( true );
            }
        } );
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JProgressBar jProgressBar1;
    // End of variables declaration//GEN-END:variables
}