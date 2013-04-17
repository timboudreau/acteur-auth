package com.timboudreau.trackerapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.Method;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.timboudreau.trackerapi.support.UserCollectionFinder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.timboudreau.trackerapi.Properties.*;

/**
 *
 * @author Tim Boudreau
 */
class ListUsersResource extends Page {

    @Inject
    ListUsersResource(ActeurFactory af) {
        add(af.matchMethods(false, Method.GET, Method.HEAD));
        add(af.matchPath("^all$"));
        add(UserCollectionFinder.class);
        add(UF.class);
    }

    @Override
    protected String getDescription() {
        return "List all users";
    }

    static class UF extends Acteur implements ChannelFutureListener {

        final DBCursor cursor;
        final AtomicBoolean first = new AtomicBoolean(true);
        private final Event evt;
        private final ObjectMapper mapper;

        @Inject
        UF(DBCollection coll, Event evt, ObjectMapper mapper) {
            this.evt = evt;
            cursor = coll.find();
            evt.getChannel().closeFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    cursor.close();
                }
            });
            this.mapper = mapper;
            setState(new RespondWith(200));
            if (evt.getMethod() == Method.GET) {
                setResponseBodyWriter(this);
            }
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (first.compareAndSet(true, false)) {
                future = future.channel().write(Unpooled.wrappedBuffer("[\n".getBytes()));
            }
            if (!cursor.hasNext()) {
                future = future.channel().write(Unpooled.wrappedBuffer("]\n".getBytes()));
                if (!evt.isKeepAlive()) {
                    future.addListener(CLOSE);
                }
            } else {
                DBObject obj = cursor.next();
                Map<?, ?> m = obj.toMap();
                m.remove(pass);
                m.remove(origPass);
                future = future.channel().write(Unpooled.wrappedBuffer(mapper.writeValueAsBytes(m)));
                if (cursor.hasNext()) {
                    future = future.channel().write(Unpooled.wrappedBuffer(",\n".getBytes()));
                }
                future.addListener(this);
            }
        }
    }
}
