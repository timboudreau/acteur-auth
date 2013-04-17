package com.timboudreau.trackerapi;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import com.timboudreau.trackerapi.support.Auth;
import com.timboudreau.trackerapi.support.CreateCollectionPolicy;
import com.timboudreau.trackerapi.support.TimeCollectionFinder;
import java.io.IOException;
import static com.timboudreau.trackerapi.Properties.*;
import com.timboudreau.trackerapi.support.AuthorizedChecker;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 *
 * @author Tim Boudreau
 */
class AdjustTimeResource extends Page {

    public static final String URL_PATTERN_ADJUST = "^users/(.*?)/adjust/(.*?)$";

    @Inject
    public AdjustTimeResource(ActeurFactory af) {
        add(af.matchPath(URL_PATTERN_ADJUST));
        add(af.matchMethods(Method.PUT, Method.POST));
        add(af.banParameters("type"));
        add(af.requireAtLeastOneParameter("shift", "moveTo", "length", "newStart", "newEnd"));
        add(af.parametersMayNotBeCombined("newStart", "shift", "moveTo"));
        add(af.parametersMayNotBeCombined("newEnd", "shift", "moveTo"));
        add(af.parametersMustBeNumbersIfTheyArePresent(false, true, "newStart", "newEnd", "shift", "moveTo"));
        add(af.parametersMustBeNumbersIfTheyArePresent(false, false, "length"));
        add(Auth.class);
        add(AuthorizedChecker.class);
        add(CreateCollectionPolicy.DONT_CREATE.toActeur());
        add(TimeCollectionFinder.class);
        add(af.injectRequestParametersAs(AdjustParameters.class));
        add(Adjuster.class);
    }

    @Override
    protected String getDescription() {
        return "Adjust records by shifting, moving or changing start, end or length";
    }

    private static class Adjuster extends Acteur {

        @Inject
        public Adjuster(Event evt, DBCollection collection, BasicDBObject query, AdjustParameters params) throws IOException {
            query.put(type, time);
            query.remove(detail);

            BasicDBObject update = new BasicDBObject();
            BasicDBObject set = new BasicDBObject();
            Long shift = params.shift();
            if (shift != null) {
                update.put("$inc", new BasicDBObject(start, shift).append(end, shift).append(version, 1));
                WriteResult res = collection.update(query, update, false, true, WriteConcern.ACKNOWLEDGED);
                setState(new RespondWith(res.getN() > 0 ? 200 : 400, Timetracker.quickJson("updated", res.getN())));
            } else {
                update.put("$inc", new BasicDBObject(version, 1));
                DBObject ob = collection.findOne(query);
                if (ob == null) {
                    setState(new RespondWith(HttpResponseStatus.GONE, "No matching object"));
                    return;
                }
                long newStart = params.newStart() == null ? (Long) ob.get(start) : params.newStart();
                long newEnd = params.newEnd() == null ? (Long) ob.get(end) : params.newEnd();
                if (params.moveTo() == null && newEnd < newStart) {
                    setState(new RespondWith(HttpResponseStatus.GONE, "Start " + newStart + " will be after end " + newEnd));
                    return;
                }
                if (params.newStart() != null || params.newEnd() != null) {
                    update.put("$set", set);
                    if (params.newStart() != null) {
                        set.put(start, newStart);
                    }
                    if (params.newEnd() != null) {
                        set.put(end, newEnd);
                    }
                    set.put(duration, newEnd - newStart);
                }
                if (params.length() != null) {
                    update.put("$set", set);
                    if (params.newEnd() != null) {
                        set.put(start, newEnd - params.length());
                    } else {
                        set.put(end, newStart + params.length());
                    }
                    set.put(duration, params.length());
                }
                if (params.moveTo() != null) {
                    update.put("$set", set);
                    long dur = params.length() == null ? newEnd - newStart : params.length();
                    set.put(start, params.moveTo());
                    set.put(duration, dur);
                    set.put(end, params.moveTo() + dur);
                }
                WriteResult res = collection.update(query, update, false, false, WriteConcern.ACKNOWLEDGED);
                setState(new RespondWith(res.getN() > 0 ? 200 : 400, Timetracker.quickJson("updated", res.getN())));
            }
        }
    }

    public interface AdjustParameters {

        public Long newStart();

        public Long moveTo();

        public Long newEnd();

        public Long shift();

        public Long length();
    }
}
