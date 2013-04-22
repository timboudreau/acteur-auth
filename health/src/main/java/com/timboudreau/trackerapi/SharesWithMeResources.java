package com.timboudreau.trackerapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.util.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.timboudreau.trackerapi.support.Auth;
import com.timboudreau.trackerapi.support.AuthSupport;
import com.timboudreau.trackerapi.support.CursorWriterActeur;
import com.timboudreau.trackerapi.support.TTUser;
import com.timboudreau.trackerapi.support.UserCollectionFinder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import java.io.IOException;

/**
 *
 * @author Tim Boudreau
 */
public class SharesWithMeResources extends Page {

    @Inject
    SharesWithMeResources(ActeurFactory af) {
        add(af.matchPath("^users/.*?/sharers/?$"));
        add(af.matchMethods(Method.GET));
        add(Auth.class);
        add(UserCollectionFinder.class);
        add(FindSharers.class);
        add(CursorWriterActeur.class);
    }

    @Override
    protected String getDescription() {
        return "Authenticate login and fetch user name";
    }
    
    private static class FindSharers extends Acteur {
        @Inject
        FindSharers(Event evt, TTUser user, DBCollection coll, ObjectMapper mapper, AuthSupport supp) throws IOException {
            add(Headers.stringHeader("UserID"), user.id.toStringMongod());
            BasicDBObject projection = new BasicDBObject("_id", 1).append("name", 1).append("displayName", 1);
            DBCursor cursor = coll.find(new BasicDBObject("authorizes", user.id), projection);
            if (cursor == null) {
                setState(new RespondWith(HttpResponseStatus.GONE, "No record of " + user.name));
                return;
            }
            if (!cursor.hasNext()) {
                setState(new RespondWith(200, "[]\n"));
                cursor.close();
            } else {
                setState(new ConsumedLockedState(cursor));
            }
        }
    }
}
