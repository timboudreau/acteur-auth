package com.timboudreau.questions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import static com.timboudreau.questions.AddSurveyResource.QUESTION_PATTERN;
import com.timboudreau.trackerapi.support.Auth;
import com.timboudreau.trackerapi.support.TTUser;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;

/**
 *
 * @author Tim Boudreau
 */
public class GetSurveysResource extends Page {

    @Inject
    GetSurveysResource(ActeurFactory af) {
        add(af.matchPath(QUESTION_PATTERN));
        add(af.matchMethods(Method.GET));
        add(Auth.class);
        add(SurveysActeur.class);
    }

    @Override
    protected String getDescription() {
        return "Get all of the surveys created by the calling user";
    }

    private static class SurveysActeur extends Acteur {

        @Inject
        SurveysActeur(TTUser user, @Named("surveys") DBCollection coll, ObjectMapper mapper, Event evt) {
            BasicDBObject query = new BasicDBObject("createdBy", user.id);
            DBCursor cursor = coll.find(query);
            if (!cursor.hasNext()) {
                setState(new RespondWith(HttpResponseStatus.OK, "[]\n"));
            } else {
                setState(new RespondWith(HttpResponseStatus.OK));
                setResponseBodyWriter(new ResultsWriter(cursor, mapper, evt));
            }
        }

        static class ResultsWriter implements ChannelFutureListener {

            private final DBCursor cursor;
            private volatile boolean first = true;
            private final ObjectMapper mapper;
            private final boolean close;

            @Inject
            public ResultsWriter(DBCursor cursor, ObjectMapper mapper, Event evt) {
                this.cursor = cursor;
                this.mapper = mapper;
                close = !evt.isKeepAlive();
            }

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                // We simply iterate via being called back until the cursor is
                // finished
                if (first) {
                    future = future.channel().write(Unpooled.copiedBuffer("[\n", CharsetUtil.UTF_8));
                    first = false;
                }
                if (cursor.hasNext()) {
                    DBObject ob = cursor.next();
                    future = future.channel().write(Unpooled.copiedBuffer(mapper.writeValueAsString(ob), CharsetUtil.UTF_8));
                }
                if (cursor.hasNext()) {
                    future = future.channel().write(Unpooled.copiedBuffer(",\n", CharsetUtil.UTF_8));
                    future.addListener(this);
                } else {
                    future = future.channel().write(Unpooled.copiedBuffer("\n]\n", CharsetUtil.UTF_8));
                    if (close) {
                        future.addListener(ChannelFutureListener.CLOSE);
                    }
                }
            }
        }
    }
}
