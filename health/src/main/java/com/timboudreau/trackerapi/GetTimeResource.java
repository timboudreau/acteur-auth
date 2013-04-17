package com.timboudreau.trackerapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.timboudreau.trackerapi.support.Auth;
import com.timboudreau.trackerapi.support.CreateCollectionPolicy;
import com.timboudreau.trackerapi.support.TimeCollectionFinder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.timboudreau.trackerapi.Properties.*;
import com.timboudreau.trackerapi.support.AuthorizedChecker;

/**
 *
 * @author Tim Boudreau
 */
class GetTimeResource extends Page {

    @Inject
    public GetTimeResource(ActeurFactory af) {
        add(af.matchPath(Timetracker.URL_PATTERN_TIME));
        add(af.matchMethods(Method.GET, Method.HEAD));
        add(af.banParameters("type"));
        add(Auth.class);
        add(AuthorizedChecker.class);
        add(CreateCollectionPolicy.DONT_CREATE.toActeur());
        add(TimeCollectionFinder.class);
        add(TimeGetter.class);
    }

    @Override
    protected String getDescription() {
        return "Query recorded time events";
    }

    private static class TimeGetter extends Acteur implements ChannelFutureListener {

        private static final ByteBuf openBracket = Unpooled.copiedBuffer(new byte[]{'[', '\n'});
        private static final ByteBuf closeBracket = Unpooled.copiedBuffer(new byte[]{'\n', ']', '\n'});
        private static final ByteBuf comma = Unpooled.copiedBuffer(new byte[]{',', '\n'});
        private final AtomicBoolean first = new AtomicBoolean(true);
        private final DBCursor cur;
        private final Event evt;
        private final ObjectMapper mapper;

        @Inject
        public TimeGetter(DBCollection collection, BasicDBObject query, Event evt, ObjectMapper mapper) {
            this.mapper = mapper;
            this.evt = evt;
            query.put(type, time);
            String fields = evt.getParameter("fields");
            DBObject projection = null;
            if (fields != null) {
                projection = new BasicDBObject();
                for (String field : fields.split(",")) {
                    System.out.println("FIELD " + field);
                    projection.put(field, 1);
                }
            }
            cur = projection == null ? collection.find(query) : collection.find(query, projection);
            evt.getChannel().closeFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    cur.close();
                }
            });
            setState(new RespondWith(200));
            if (!cur.hasNext()) {
                setMessage("[]");
            } else {
                if (evt.getMethod() != Method.HEAD && evt.getChannel().isOpen()) {
                    setResponseBodyWriter(this);
                }
            }
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (first.compareAndSet(true, false)) {
                future = future.channel().write(openBracket.copy());
            }
            if (!cur.hasNext()) {
                future = future.channel().write(closeBracket.copy());
                if (!evt.isKeepAlive()) {
                    future.addListener(ChannelFutureListener.CLOSE);
                }
                return;
            }
            DBObject obj = cur.next();
            Map<?, ?> m = obj.toMap();
            ByteBuf b = Unpooled.wrappedBuffer(mapper.writeValueAsBytes(m));
            future = future.channel().write(b);
            if (cur.hasNext()) {
                future.channel().write(comma.copy());
            }
            future.addListener(this);
        }
    }
}
