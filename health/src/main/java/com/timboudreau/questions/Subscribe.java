package com.timboudreau.questions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.Method;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import io.netty.util.CharsetUtil;
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
        private final Set<Channel> channels = new CopyOnWriteArraySet<>();
        private final ObjectMapper mapper;

        @Inject
        public Publisher(ObjectMapper mapper) {
            this.mapper = mapper;
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

        public void publish(String eventType, Object object) throws JsonProcessingException {
            StringBuilder sb = new StringBuilder("id: ")
                    .append(DateTimeUtils.currentTimeMillis()).append('\n')
                    .append("data: ").append(mapper.writeValueAsString(object))
                    .append("\n\n");
            ByteBuf buf = Unpooled.copiedBuffer(sb, CharsetUtil.UTF_8);
            for (Channel channel : channels) {
                channel.write(buf.copy());
            }
        }
    }

    private static class PushActeur extends Acteur implements ChannelFutureListener {

        private final Publisher publisher;
        private final Event evt;

        @Inject
        PushActeur(Publisher publisher, Event evt) throws JsonProcessingException {
            this.publisher = publisher;
            setState(new RespondWith(OK));
            setResponseBodyWriter(this);
            this.evt = evt;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            String eventType = evt.getPath().getElement(1).toString();
            // Add here - otherwise we can be sent events before
            // the HTTP headers have been written to the socket
            publisher.add(eventType, future.channel());
        }
    }
}