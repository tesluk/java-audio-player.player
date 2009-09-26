package javaaudiotest.player;

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
            if( old > 0 && (System.currentTimeMillis() - lastTime) > 15000 ) {
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

    @Override
    public void run() {
        PlayerState lastState = parent.getState();
        long lastPosition = parent.getCurrentPosition();

        try {
            notifyListenerState();
            while( !dieRequest ) {
                synchronized( parent.osync ) {
                    parent.osync.wait();
                }

                if( lastState != parent.getState() || lastPosition != parent.getCurrentPosition() )
                    notifyListenerState();

                if( parent.getState() == PlayerState.STOPPED || parent.getState() == PlayerState.PAUSED ) {
                    synchronized( osync ) {
                        dieRequest = true;
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
                System.out.println( "call listener" );
                PlayerEventListener l = parent.getListener();
                if( l != null )
                    l.stateChanged();
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
