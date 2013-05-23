package com.timboudreau.trackerapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.AuthenticationActeur;
import com.mastfrog.acteur.mongo.userstore.TTUser;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.util.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.WriteConcern;
import static com.timboudreau.trackerapi.Properties.*;
import static com.timboudreau.trackerapi.RecordTimeConnectionIsOpenResource.LiveTime.buildQueryFromURLParameters;
import com.timboudreau.trackerapi.support.AuthorizedChecker;
import com.timboudreau.trackerapi.support.CreateCollectionPolicy;
import com.timboudreau.trackerapi.support.TimeCollectionFinder;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.util.Map;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Interval;

/**
 *
 * @author Tim Boudreau
 */
final class AddTimeResource extends Page {

    @Inject
    AddTimeResource(ActeurFactory af) {
        add(af.matchPath(Timetracker.URL_PATTERN_TIME));
        add(af.matchMethods(Method.PUT, Method.POST));
        add(af.requireParameters("start", "end"));
        add(af.banParameters("added", "type"));
        add(CheckParameters.class);
        add(CreateCollectionPolicy.CREATE.toActeur());
        add(AuthenticationActeur.class);
        add(AuthorizedChecker.class);
        add(TimeCollectionFinder.class);
        add(TimeAdder.class);
    }

    @Override
    protected String getDescription() {
        return "Add A Time Event";
    }

    private static class CheckParameters extends Acteur {

        @Inject
        CheckParameters(Event evt) {
            System.out.println("AddTimeResource checkParameters");
            try {
                DateTime startTime = new DateTime(evt.getLongParameter(start).get());
                DateTime endTime = new DateTime(evt.getLongParameter(end).get());
                DateTime now = DateTime.now();
                DateTime twentyYearsAgo = now.minus(Duration.standardDays(365 * 20));
                if (twentyYearsAgo.isAfter(startTime)) {
                    setState(new RespondWith(HttpResponseStatus.BAD_REQUEST,
                            "Too long ago - minimum is " + twentyYearsAgo));
                    return;
                }
                Interval interval = new Interval(startTime, endTime);
                setState(new ConsumedLockedState(interval));
            } catch (NumberFormatException e) {
                setState(new RespondWith(400, "Start or end is not a number: '" + evt.getParameter(start) + "' and '" + evt.getParameter(end)));
            }
        }
    }
    static final int MAX_PROPERTIES = 10;

    private static class TimeAdder extends Acteur {

        @Inject
        TimeAdder(Event evt, DBCollection coll, ObjectMapper mapper, TTUser user, Interval interval) throws IOException {
            long startVal = interval.getStartMillis();
            long endVal = interval.getEndMillis();
            // XXX ability to add for a different user?
            if (endVal - startVal <= 0) {
                setState(new RespondWith(400, "Start is equal to or after end '"
                        + interval.getStart() + "' and '" + interval.getEnd() + "'"));
                return;
            }
            BasicDBObject toWrite = new BasicDBObject(type, time)
                    .append(start, startVal)
                    .append(end, endVal)
                    .append(duration, endVal - startVal)
                    .append(added, DateTime.now().getMillis())
                    .append(by, user.idAsString())
                    .append(version, 0);

            if (toWrite.get(start) instanceof String) {
                throw new IOException("Bad bad bad: " + toWrite.get(start));
            }

            String err = buildQueryFromURLParameters(evt, toWrite, Properties.start, Properties.end, Properties.duration);
            if (err != null) {
                setState(new RespondWith(400, err));
                return;
            }
            if (toWrite.get(start) instanceof String) {
                throw new IOException("Bad bad bad: " + toWrite.get(start));
            }
            coll.insert(toWrite, WriteConcern.SAFE);
            Map m = toWrite.toMap();
            ObjectId id = (ObjectId) m.get(_id);
            if (id != null) {
                add(Headers.stringHeader("X-Tracker-ID"), id.toString());
                if (evt.getParameter("localId") != null) {
                    add(Headers.stringHeader("X-Local-ID"), evt.getParameter("localId"));
                }
            }
            setState(new RespondWith(HttpResponseStatus.ACCEPTED, mapper.writeValueAsString(m)));
        }
    }
}
