/**
 *
 * (c) Sergey Mashkov (aka cy6erGn0m), 2009
 *
 * License: GNU LGPL v3
 * To read license read lgpl-3.0.txt from root of repository or follow URL:
 *      http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 */

package maryb.player;

import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author cy6ergn0m
 */
/* package */ class PlayerListenerNotificatorThread extends Thread {

    private volatile boolean dieRequest = false;

    private final Player parent;

    private final PlayerThread creator;

    private AtomicLong eventCount = new AtomicLong();

    private final Object osync = new Object();

    private NotificationWrapper w = null;

    private long lastTime = 0;

    public PlayerListenerNotificatorThread( Player parent, PlayerThread creator ) {
        super( "player-notification-dispatcher-thread" );
        this.parent = parent;
        this.creator = creator;
    }

    private void notifyListenerState() throws InterruptedException {
        long old = eventCount.getAndIncrement();
        if( w == null || !w.isAlive() ) {
            w = new NotificationWrapper();
            w.start();
        } else {
            if( old > 0 && ( System.currentTimeMillis() - lastTime ) > 15000 ) {
                w.interrupt();
                w = null;
                notifyListenerState();
                return;
            }
        }
        synchronized( osync ) {
            osync.notifyAll();
        }
        lastTime = System.currentTimeMillis();
    }

    private boolean isStopped() {
        return parent.getState() == PlayerState.STOPPED || parent.getState() == PlayerState.PAUSED;
    }

    private boolean aliveCriterion() {
        return !dieRequest && !(!isStopped() && !creator.isAlive()) &&
                ( ( parent.getPumpStream() != null && !parent.getPumpStream().isAllDownloaded() ) ||
                ( !isStopped() && creator.isAlive() ) );
    }

    @Override
    public void run() {
        PlayerState lastState = parent.getState();
        long lastPosition = parent.getCurrentPosition();
        long lastBufferedPosition = parent.getCurrentBufferedTimeMcsec();
        long lastTotalTime = parent.getTotalPlayTimeMcsec();

        try {
            notifyListenerState();

            boolean chk = false;
            while( aliveCriterion() ) {
                while( aliveCriterion() ) {
                    chk = lastState != parent.getState() ||
                            lastPosition != parent.getCurrentPosition() ||
                            lastBufferedPosition != parent.getCurrentBufferedTimeMcsec() ||
                            lastTotalTime != parent.getTotalPlayTimeMcsec();

                    if( chk )
                        break;

                    synchronized( parent.osync ) {
                        parent.osync.wait();
                    }
                }

                if( chk ) {
                    notifyListenerState();
                    sleep( 100 );
                }

                lastState = parent.getState();
                lastPosition = parent.getCurrentPosition();
                lastBufferedPosition = parent.getCurrentBufferedTimeMcsec();
                lastTotalTime = parent.getTotalPlayTimeMcsec();

                if( isStopped() ) {
                    dieRequest = !aliveCriterion();
                    synchronized( osync ) {
                        osync.notifyAll();
                    }
                }
            }
        } catch( InterruptedException ignore ) {
        } finally {
            dieRequest = true;
            synchronized( osync ) {
                osync.notifyAll();
            }
        }
    }

    public void die() {
        dieRequest = true;
        interrupt();
    }

    private class NotificationWrapper extends Thread {

        public NotificationWrapper() {
            super( "player-notification-end-executor-thread" );
        }

        private void call() {
            try {
                PlayerEventListener l = parent.getListener();
                if( l != null )
                    l.stateChanged();
            } catch( Throwable t ) {
                t.printStackTrace();
            }
        }

        private void callEOM() {
            try {
                PlayerEventListener l = parent.getListener();
                if( l != null )
                    l.endOfMedia();
            } catch( Throwable t ) {
                t.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                while( !dieRequest && !isInterrupted() ) {
                    synchronized( osync ) {
                        while( !dieRequest && eventCount.get() == 0 )
                            osync.wait();
                    }

                    do {
                        call();
                        if( parent.getState() == PlayerState.STOPPED && parent.isEndOfMediaReached() )
                            callEOM();
                    } while( eventCount.decrementAndGet() > 0 );

                    synchronized( osync ) {
                        osync.notifyAll();
                    }
                }
                synchronized( osync ) {
                    osync.notifyAll();
                }
            } catch( InterruptedException ignore ) {
            }
        }

    }

}
