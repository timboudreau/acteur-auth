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
        add(Sharers.class);
    }

    @Override
    protected String getDescription() {
        return "Authenticate login and fetch user name";
    }

    private static class Sharers extends Acteur implements ChannelFutureListener {

        private final DBCursor cursor;
        private volatile boolean first = true;
        private final ObjectMapper mapper;
        private final Event evt;

        @Inject
        Sharers(Event evt, TTUser user, DBCollection coll, ObjectMapper mapper, AuthSupport supp) throws IOException {
            this.mapper = mapper;
            this.evt = evt;
            add(Headers.stringHeader("UserID"), user.id.toStringMongod());
            BasicDBObject projection = new BasicDBObject("_id", 1).append("name", 1).append("displayName", 1);
            cursor = coll.find(new BasicDBObject("authorizes", user.id), projection);
            if (cursor == null) {
                setState(new RespondWith(HttpResponseStatus.GONE, "No record of " + user.name));
                return;
            }
            if (!cursor.hasNext()) {
                setState(new RespondWith(200, "[]\n"));
            } else {
                setState(new RespondWith(200));
                setResponseBodyWriter(this);
            }
        }

        void finish(ChannelFuture future) {
            cursor.close();
            future = future.channel().write(Unpooled.wrappedBuffer("\n]\n".getBytes(CharsetUtil.UTF_8)));
            if (!evt.isKeepAlive()) {
                future.addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (!future.channel().isOpen()) {
                cursor.close();
                return;
            }
            if (first) {
                first = false;
                future = future.channel().write(Unpooled.wrappedBuffer("[\n".getBytes(CharsetUtil.UTF_8)));
            }
            if (cursor.hasNext()) {
                DBObject ob = cursor.next();
                future = future.channel().write(Unpooled.wrappedBuffer(mapper.writeValueAsBytes(ob.toMap())));
                if (cursor.hasNext()) {
                    future = future.channel().write(Unpooled.wrappedBuffer(",\n".getBytes(CharsetUtil.UTF_8)));
                    future.addListener(this);
                } else {
                    finish(future);
                }
            } else {
                finish(future);
            }
        }
    }
}
