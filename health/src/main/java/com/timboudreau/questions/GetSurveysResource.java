package com.timboudreau.questions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.util.Providers;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.ResponseWriter;
import com.mastfrog.acteur.auth.Auth;
import com.mastfrog.acteur.mongo.CursorWriter;
import com.mastfrog.acteur.mongo.CursorWriter.MapFilter;
import com.mastfrog.acteur.mongo.userstore.TTUser;
import com.mastfrog.acteur.util.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import static com.timboudreau.questions.AddSurveyResource.QUESTION_PATTERN;
import com.timboudreau.trackerapi.Properties;
import com.timboudreau.trackerapi.support.UserCollectionFinder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
public class GetSurveysResource extends Page {

    @Inject
    GetSurveysResource(ActeurFactory af, Event evt) {
        add(af.matchPath(QUESTION_PATTERN));
        add(af.matchMethods(Method.GET));
        add(UserCollectionFinder.class);
        add(Auth.class);
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
        SurveysActeur(TTUser user, @Named("surveys") DBCollection coll, DBCollection users, ObjectMapper mapper, Event evt) {
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
                setResponseWriter(new CursorWriter(cursor, evt, Providers.<MapFilter>of(new MapFilter() {

                    @Override
                    public Map<String, Object> filter(Map<String, Object> m) {
                        return m;
                    }
                    
                })));
            }
        }
    }
}
