package com.timboudreau.trackerapi;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Acteur.RespondWith;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.AuthenticationActeur;
import com.mastfrog.acteur.headers.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import static com.timboudreau.trackerapi.Properties.*;
import com.timboudreau.trackerapi.support.CreateCollectionPolicy;
import com.timboudreau.trackerapi.support.TimeCollectionFinder;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 *
 * @author tim
 */
public final class ModifyEventsResource extends Page {

    public static final String PAT = "^users/(.*?)/update/(.*?)/(.*?)$";

    @Inject
    public ModifyEventsResource(HttpEvent evt, ActeurFactory af) {
        add(af.matchPath(PAT));
        add(af.matchMethods(Method.PUT, Method.POST, Method.DELETE));
        add(af.banParameters(type));
        add(AuthenticationActeur.class);
        add(CreateCollectionPolicy.DONT_CREATE.toActeur());
        add(TimeCollectionFinder.class);
        if (evt.getMethod() != Method.DELETE) {
            add(af.injectRequestBodyAsJSON(Body.class));
        } else {
            add(FakeBody.class);
        }
        add(Modifier.class);
    }

    private static class FakeBody extends Acteur {

        FakeBody() {
            next(new Body(1));
        }
    }

    @Override
    protected String getDescription() {
        return "Modify or add data to an event or set of events";
    }

    private static class Modifier extends Acteur {

        @Inject
        public Modifier(HttpEvent evt, DBCollection collection, BasicDBObject query, Body something) throws IOException {
            query.put(type, time);
            boolean isDelete = evt.getMethod() == Method.DELETE;
            DBObject modification = new BasicDBObject(isDelete ? "$unset"
                    : "$set", new BasicDBObject(evt.getPath().getLastElement().toString(),
                    something.object)).append("$inc", new BasicDBObject("version", 1));
            WriteResult res = collection.update(query, modification, false, true, WriteConcern.ACKNOWLEDGED);
            String resultJson = Timetracker.quickJson("updated", res.getN());
            setState(new RespondWith(res.getN() > 0 ? HttpResponseStatus.ACCEPTED
                    : HttpResponseStatus.GONE, resultJson));
        }
    }

    static class Body {

        // This class exists because Jackson will not instantiate String or number directly,
        // but will instantiate an object that takes a String or number as
        // an argument
        private final Object object;

        Body(int number) {
            this.object = number;
        }

        Body(String value) {
            this.object = value;
        }

        Body(Map map) {
            this.object = map;
        }

        Body(List list) {
            this.object = list;
        }

        public Object get() {
            return object;
        }

        @Override
        public String toString() {
            return object.toString();
        }
    }
}
