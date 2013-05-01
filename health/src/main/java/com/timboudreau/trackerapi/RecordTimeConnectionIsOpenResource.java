package com.timboudreau.trackerapi;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.Auth;
import com.mastfrog.acteur.mongo.userstore.TTUser;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.util.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.WriteConcern;
import com.timboudreau.trackerapi.support.CreateCollectionPolicy;
import com.timboudreau.trackerapi.support.LiveWriter;
import com.timboudreau.trackerapi.support.TimeCollectionFinder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import static com.timboudreau.trackerapi.Properties.*;
import com.timboudreau.trackerapi.support.AuthorizedChecker;
import java.util.Arrays;
import org.joda.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
final class RecordTimeConnectionIsOpenResource extends Page {

    @Inject
    RecordTimeConnectionIsOpenResource(ActeurFactory af) {
        add(af.matchPath("^users/(.*?)/sessions/(.*?)"));
        add(af.matchMethods(true, Method.PUT, Method.POST));
        add(Auth.class);
        add(AuthorizedChecker.class);
        add(CreateCollectionPolicy.CREATE.toActeur());
        add(TimeCollectionFinder.class);
        add(LiveTime.class);
    }

    @Override
    protected String getDescription() {
        return "Record an ongoing time event which lasts as long as the connection to this"
                + " URL is held open";
    }

    static final class LiveTime extends Acteur implements ChannelFutureListener {

        private final BasicDBObject toWrite = new BasicDBObject(type, time);
        private final long created = DateTime.now().getMillis();
        private final AtomicBoolean isRunning = new AtomicBoolean(true);

        @Inject
        LiveTime(@Named("periodicLiveWrites") final boolean pings, final Event evt, final Provider<DBCollection> coll, TTUser user, Application application, final Provider<LiveWriter> writer) {
            System.out.println("RecordTimeConnectionIsOpenResource LiveTime init");
            toWrite.append(by, user.idAsString())
                    .append(start, created)
                    .append(end, created)
                    .append(running, true)
                    .append(added, created)
                    .append(version, 0);
            String err = buildQueryFromURLParameters(evt, toWrite);
            if (err != null) {
                setState(new RespondWith(400, err));
                return;
            }
            add(Headers.CONTENT_LENGTH, 380L);
            add(Headers.stringHeader("X-Remote-Start"), created + "");
            add(Headers.DATE, new DateTime(created));
            add(Headers.stringHeader("Connection"), "keep-alive");
            add(Headers.stringHeader("X-Accel-Buffering"), "off");
            add(Headers.stringHeader("Keep-Alive"), "timeout=" + Duration.standardDays(365).getStandardSeconds());
            setChunked(false);
            setState(new RespondWith(HttpResponseStatus.ACCEPTED));
            setResponseBodyWriter(this);
            coll.get().insert(toWrite, WriteConcern.FSYNC_SAFE);
            ObjectId id = (ObjectId) toWrite.get(_id);
            add(Headers.stringHeader("X-Tracker-ID"), id.toStringMongod());
            if (evt.getParameter("localId") != null) {
                add(Headers.stringHeader("X-Local-ID"), evt.getParameter(localId));
            }

            final AtomicBoolean done = new AtomicBoolean();
            final Callable<?> c = application.getRequestScope()
                    .wrap(new PeriodicDurationUpdater(toWrite, coll, done,
                    isRunning, created));

            if (pings) {
                writer.get().add(c);
            }
            evt.getChannel().closeFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    System.out.println("Write recorded time on connection closed");
                    isRunning.set(false);
                    try {
                        c.call();
                    } finally {
                        done.set(true);
                        if (pings) {
                            writer.get().remove(c);
                        }
                    }
                }
            });
        }

        static boolean undot(String key, Object val, BasicDBObject ob) {
            System.out.println("KEY " + key + " val " + val);
            if (key.length() == 0) {
                return false;
            }
            if (key.indexOf('.') < 0) {
                ob.put(key, val);
                return true;
            } else {
                int ix = key.indexOf('.');
                String rest = key.substring(ix + 1);
                String first = key.substring(0, ix);
                if (first.length() == 0 || first.charAt(0) == '$' || first.charAt(0) == '$') {
                    return false;
                }
                BasicDBObject sub = new BasicDBObject();
                ob.put(first, sub);
                return undot(rest, val, sub);
            }
        }

        static String buildQueryFromURLParameters(final Event evt, BasicDBObject toWrite, String... ignore) {
            Arrays.sort(ignore);
            if (evt.getParametersAsMap().size() > AddTimeResource.MAX_PROPERTIES) {
                return "Too many URL parameters - max is "
                        + AddTimeResource.MAX_PROPERTIES;
            }
            for (Map.Entry<String, String> e : evt.getParametersAsMap().entrySet()) {
                switch (e.getKey()) {
                    case start:
                    case end:
                    case version:
                    case running:
                    case added:
                    case duration:
                    case by:
                        if (Arrays.binarySearch(ignore, e.getKey()) < 0) {
                            return "Illegal query parameter '" + e.getKey() + "'";
                        }
                        break;
                    default:
                        String v = e.getValue();
                        if (v.charAt(0) == '.' || v.charAt(v.length() - 1) == '.' || v.charAt(0) == '$') {
                            return "Parameter name may not start or end with . or start with $";
                        }
                        if (v.indexOf(',') > 0) {
                            String[] spl = v.split(",");
                            List<String> l = new LinkedList<>();
                            for (String s : spl) {
                                l.add(s.trim());
                            }
                            if (!undot(e.getKey(), l, toWrite)) {
                                return "Empty sub-property name or presence of . or $ in " + e.getKey();
                            }
                        } else {
                            if (!undot(e.getKey(), e.getValue(), toWrite)) {
                                return "Empty sub-property name or presence of . or $ in " + e.getKey();
                            }
                        }
                }
            }
            return null;
        }

        static class PeriodicDurationUpdater implements Callable<Void> {

            private final BasicDBObject toWrite;
            private final Provider<DBCollection> coll;
            private final AtomicBoolean done;
            private final AtomicBoolean running;
            private final long start;

            public PeriodicDurationUpdater(BasicDBObject toWrite, Provider<DBCollection> coll, AtomicBoolean done, AtomicBoolean running, long start) {
                this.toWrite = toWrite;
                this.coll = coll;
                this.done = done;
                this.running = running;
                this.start = start;
            }

            public Void call() throws Exception {
                if (!done.get()) {
                    long end = DateTime.now().getMillis();
                    System.out.println("Write time " + new Duration(end - start));
                    toWrite.append("end", end).append(Properties.duration, end - start).append(Properties.running, running.get());
                    coll.get().save(toWrite, WriteConcern.UNACKNOWLEDGED);
                }
                return null;
            }
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            future.channel().write(Unpooled.wrappedBuffer(("Started at " + toWrite.get(Properties.start)).getBytes()));
            // don't close!
        }
    }
}
