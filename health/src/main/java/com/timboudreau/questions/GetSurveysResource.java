package com.timboudreau.questions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.util.Providers;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Closables;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.AuthenticationActeur;
import com.mastfrog.acteur.mongo.CursorWriter;
import com.mastfrog.acteur.mongo.CursorWriter.MapFilter;
import com.mastfrog.acteur.mongo.userstore.TTUser;
import com.mastfrog.acteur.headers.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import static com.timboudreau.questions.AddSurveyResource.QUESTION_PATTERN;
import com.timboudreau.trackerapi.Properties;
import com.timboudreau.trackerapi.support.UserCollectionFinder;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
public class GetSurveysResource extends Page {

    @Inject
    GetSurveysResource(ActeurFactory af, HttpEvent evt) {
        add(af.matchPath(QUESTION_PATTERN));
        add(af.matchMethods(Method.GET));
        add(UserCollectionFinder.class);
        add(AuthenticationActeur.class);
        add(SurveysActeur.class);
    }

    @Override
    protected String getDescription() {
        return "Get all of the surveys created by the calling user or the "
                + "user name specified by the <code>?user=</code> url parameter";
    }

    private static class SurveysActeur extends Acteur {

        @Inject
        @SuppressWarnings("element-type-mismatch")
        SurveysActeur(TTUser user, @Named("surveys") DBCollection coll, DBCollection users, Closables clos, ObjectMapper mapper, HttpEvent evt) {
            String pathId = evt.getPath().getElement(1).toString();
            BasicDBObject query;
            if (!user.names().contains(pathId)) {
                BasicDBObject nameQuery = new BasicDBObject(Properties.name, pathId);
                DBObject otherUser = users.findOne(nameQuery);
                if (otherUser == null) {
                    setState(new RespondWith(HttpResponseStatus.GONE, "No such user'" + pathId + "'\n"));
                    return;
                } else {
                    List<ObjectId> authorized = (List<ObjectId>) otherUser.get(Properties.authorizes);
                    if (!authorized.contains(user.id())) {
                        setState(new RespondWith(HttpResponseStatus.FORBIDDEN,
                                "You don't have permission to access " + otherUser.get(Properties.displayName) + "\n"));
                        return;
                    }
                    query = new BasicDBObject("createdBy", otherUser.get("_id"));
                }
            } else {
                query = new BasicDBObject("createdBy", user.id());
            }
            DBCursor cursor = coll.find(query);
            if (!cursor.hasNext()) {
                setState(new RespondWith(HttpResponseStatus.OK, "[]\n"));
            } else {
                setState(new RespondWith(HttpResponseStatus.OK));
//                setResponseWriter(new ResultsWriter(cursor, mapper, evt));
                setResponseWriter(new CursorWriter(cursor, clos, evt, Providers.<MapFilter>of(new MapFilter() {

                    @Override
                    public Map<String, Object> filter(Map<String, Object> m) {
                        return m;
                    }
                    
                })));
            }
        }
    }
}
