package com.timboudreau.trackerapi;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.Auth;
import com.mastfrog.acteur.util.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.timboudreau.trackerapi.support.AuthorizedChecker;
import com.timboudreau.trackerapi.support.CreateCollectionPolicy;
import com.timboudreau.trackerapi.support.TimeCollectionFinder;

/**
 *
 * @author tim
 */
public class DistinctResource extends Page {

    public static final String URL_PATTERN_DISTINCT = "^users/(.*?)/time/(.*?)/distinct$";

    @Inject
    DistinctResource(ActeurFactory af) {
        add(af.matchPath(URL_PATTERN_DISTINCT));
        add(af.matchMethods(Method.GET));
        add(af.requireParameters("field"));
        add(Auth.class);
        add(AuthorizedChecker.class);
        add(CreateCollectionPolicy.DONT_CREATE.toActeur());
        add(TimeCollectionFinder.class);
        add(DistinctFinder.class);
    }

    private static final class DistinctFinder extends Acteur {

        @Inject
        DistinctFinder(Event evt, DB db, DBCollection coll) {
            String field = evt.getParameter("field");
            BasicDBObject cmd = new BasicDBObject("distinct", coll.getName()).append("key", field);
            CommandResult res = db.command(cmd);
            setState(new RespondWith(200, res.get("values")));
        }
    }
}
