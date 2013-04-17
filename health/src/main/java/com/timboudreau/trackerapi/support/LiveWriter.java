package com.timboudreau.trackerapi.support;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.guicy.annotations.Defaults;
import com.mastfrog.guicy.annotations.Namespace;
import com.timboudreau.trackerapi.Timetracker;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps a collection of callables which update the database periodically
 * for as long as they're alive.
 *
 * @author Tim Boudreau
 */
@Singleton
@Defaults(namespace =
        @Namespace(Timetracker.TIMETRACKER), value = "liveWriteInterval=120000")
@Namespace(Timetracker.TIMETRACKER)
public final class LiveWriter {

    private final Set<Callable<?>> all = Collections.synchronizedSet(new HashSet<Callable<?>>());
    private final TT writer = new TT();

    @Inject
    LiveWriter(@Named("liveWriteInterval") long interval, ShutdownHookRegistry reg) {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(writer, 0, interval);
        reg.add(writer);
    }

    public void add(Callable<?> c) {
        all.add(c);
    }

    public void remove(Callable<?> c) {
        all.remove(c);
    }

    private class TT extends TimerTask implements Runnable {

        @Override
        public void run() {
            for (Callable<?> c : all) {
                try {
                    c.call();
                } catch (Exception ex) {
                    Logger.getLogger(LiveWriter.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
}
