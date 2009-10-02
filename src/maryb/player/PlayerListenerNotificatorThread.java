package maryb.player;

import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author cy6ergn0m
 */
public class PlayerListenerNotificatorThread extends Thread {

    private volatile boolean dieRequest = false;

    private final Player parent;

    private AtomicLong eventCount = new AtomicLong();

    private final Object osync = new Object();

    private NotificationWrapper w = null;

    private long lastTime = 0;

    public PlayerListenerNotificatorThread( Player parent ) {
        super( "player-notification-dispatcher-thread" );
        this.parent = parent;
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

    private boolean aliveCriterion() {
        return (parent.getState() != PlayerState.STOPPED && parent.getState() != PlayerState.PAUSED) ||
                (parent.getPumpStream() != null && !parent.getPumpStream().isAllDownloaded() );
    }

    @Override
    public void run() {
        PlayerState lastState = parent.getState();
        long lastPosition = parent.getCurrentPosition();
        long lastBufferedPosition = parent.getCurrentBufferedTimeMcsec();
        long lastTotalTime = parent.getTotalPlayTimeMcsec();

        try {
            notifyListenerState();
            long now, lastReaction = 0;
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

                if( parent.getState() == PlayerState.STOPPED || parent.getState() == PlayerState.PAUSED ) {
                    dieRequest = !aliveCriterion();
                    synchronized( osync ) {
                        osync.notifyAll();
                    }
                }
            }
        } catch( InterruptedException ignore ) {
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
