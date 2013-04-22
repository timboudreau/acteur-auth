package com.timboudreau.questions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.joda.time.DateTimeUtils;

/**
 *
 * @author tim
 */
public class PushTest extends Page {
    @Inject
    public PushTest(ActeurFactory af) {
        add(af.matchPath("^events$"));
        add(af.matchMethods(Method.GET));
        add(PushActeur.class);
        super.getReponseHeaders().setContentType(
                MediaType.parse("text/event-stream").withCharset(CharsetUtil.UTF_8));
    }

    @Singleton
    public static final class Publisher {

        private final Set<Channel> channels = new CopyOnWriteArraySet<>();
        private final ObjectMapper mapper;
        @Inject
        public Publisher(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        public void add(final Channel channel) {
            channel.closeFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    channels.remove(channel);
                }
            });
            channels.add(channel);
        }

        public void publish(Object object) throws JsonProcessingException {
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
        @Inject
        PushActeur(Publisher publisher, Event evt) throws JsonProcessingException {
            this.publisher = publisher;
            setState(new RespondWith(OK));

            //publish that this connection showed up, so the demo does something
            publisher.publish(Collections.singletonMap("arrived",
                    evt.getChannel().remoteAddress().toString()));
            setResponseBodyWriter(this);
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            // Add here - otherwise we can be sent events before
            // the HTTP headers have been written to the socket
            publisher.add(future.channel());
        }
    }
}
