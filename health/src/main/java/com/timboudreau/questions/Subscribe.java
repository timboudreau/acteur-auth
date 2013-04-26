package com.timboudreau.questions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.joda.time.DateTimeUtils;

/**
 * Does server-side push events - subscribe by calling /subscribe/eventType
 *
 * @author tim
 */
public class Subscribe extends Page {

    @Inject
    public Subscribe(ActeurFactory af) {
        add(af.matchPath("^subscribe/[^/]*$"));
        add(af.matchMethods(Method.GET));
        add(PushActeur.class);
        super.getReponseHeaders().setContentType(
                MediaType.parse("text/event-stream").withCharset(CharsetUtil.UTF_8));
    }

    @Singleton
    public static final class Publisher {

        private final Map<String, Set<Channel>> subscriptions = Maps.newConcurrentMap();
        private final ObjectMapper mapper;
        private final DBCollection evts;

        @Inject
        public Publisher(ObjectMapper mapper, @Named("events") DBCollection evts) {
            this.mapper = mapper;
            this.evts = evts;
        }

        public void add(String eventType, final Channel channel) {
            Set<Channel> channels = subscriptions.get(eventType);
            if (channels == null) {
                channels = new CopyOnWriteArraySet<>();
                subscriptions.put(eventType, channels);
            }
            final Set<Channel> theChannels = channels;
            channel.closeFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    theChannels.remove(channel);
                }
            });
            channels.add(channel);
        }

        public void publish(String eventType, Object object) throws JsonProcessingException, IOException {
            publish(eventType, object, true);
        }

        public void publish(String eventType, Object object, boolean addToDB) throws JsonProcessingException, IOException {
            long now = DateTimeUtils.currentTimeMillis();
            String record = mapper.writeValueAsString(object);
            StringBuilder sb = new StringBuilder("id: ")
                    .append(now).append('\n')
                    .append("data: ").append(record)
                    .append("\n\n");
            ByteBuf buf = Unpooled.copiedBuffer(sb, CharsetUtil.UTF_8);
            Set<Channel> channels = subscriptions.get(eventType);
            try {
                if (channels != null) {
                    for (Channel channel : channels) {
                        channel.write(buf.copy());
                    }
                }
            } finally {
                if (addToDB) {
                    Map m = new HashMap(mapper.readValue(record, Map.class));
                    DBObject ob = new BasicDBObject(m).append("id", now).append("type", eventType);
                    evts.insert(ob);
                }
            }
        }
    }

    private static class PushActeur extends Acteur implements ChannelFutureListener {

        private final Publisher publisher;
        private final Event evt;
        private final DBCollection evts;
        private final ObjectMapper mapper;

        @Inject
        PushActeur(Publisher publisher, Event evt, @Named("events") DBCollection evts, ObjectMapper mapper) throws JsonProcessingException {
            this.publisher = publisher;
            this.evt = evt;
            setState(new RespondWith(OK));
            setResponseBodyWriter(this);
            this.evts = evts;
            this.mapper = mapper;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            String eventType = evt.getPath().getElement(1).toString();

            BasicDBObject query = new BasicDBObject("type", eventType);

            List<DBObject> events = evts.find(query).sort(new BasicDBObject("$natural", 1)).toArray();
            for (DBObject event : events) {
                Object id = event.get("id");
                StringBuilder sb = new StringBuilder("id: ").append(id).append("\n").append("data: ");
                event.removeField("id");
                event.removeField("_id");
                sb.append(mapper.writeValueAsString(event)).append("\n\n");
                future = future.channel().write(Unpooled.copiedBuffer(sb, CharsetUtil.UTF_8));
            }

            // Add here - otherwise we can be sent events before
            // the HTTP headers have been written to the socket
            publisher.add(eventType, future.channel());
        }
    }
}
