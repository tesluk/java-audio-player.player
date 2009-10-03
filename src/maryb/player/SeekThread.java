package maryb.player;

/**
 *
 * @author cy6ergn0m
 */
/* package */ class SeekThread extends Thread {

    private final Player parent;

    public SeekThread( Player parent ) {
        super( "player-seek-thread" );
        this.parent = parent;
    }

    @Override
    public void run() {
        try {
            while( true ) {
                synchronized(parent.seekSync) {
                    if( parent.currentSeekTo == -1L ) {
                        parent.seekThread = null;
                        parent.seekSync.notifyAll();
                        return;
                    }
                }
                PlayerState st = parent.getState();
                parent.stopSync();
                
                synchronized(parent.seekSync) {
                    parent.currentSeekPositionMcsec = parent.currentSeekTo;
                    parent.currentSeekTo = -1L;
                }

                switch( st ) {
                    case PAUSED_BUFFERING:
                    case PLAYING:
                        parent.play();
                        break;
                    case PAUSED:
                        synchronized(parent.osync) {
                            parent.setState( st );
                            parent.osync.notifyAll();
                        }
                }
            }
        } catch( InterruptedException ignore ) {
        } finally {
        }
    }

}
