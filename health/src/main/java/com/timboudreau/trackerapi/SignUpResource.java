package com.timboudreau.trackerapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.util.Method;
import com.mastfrog.acteur.util.PasswordHasher;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import com.timboudreau.questions.Subscribe.Publisher;
import com.timboudreau.trackerapi.support.UserCollectionFinder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import static com.timboudreau.trackerapi.Properties.*;
import com.timboudreau.trackerapi.support.AuthSupport;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.ServerCookieEncoder;

/**
 *
 * @author Tim Boudreau
 */
class SignUpResource extends Page {

    @Inject
    SignUpResource(ActeurFactory af) {
        add(af.matchPath("^users/(.*?)/signup$"));
        add(af.matchMethods(true, Method.PUT, Method.POST));
        add(af.maximumPathLength(3));
        add(af.requireParameters("displayName"));
        add(UserCollectionFinder.class);
        add(SignerUpper.class);
    }

    @Override
    protected String getDescription() {
        return "New User Signup";
    }

    static class SignerUpper extends Acteur implements ChannelFutureListener {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream(40);
        public static final int MAX_PASSWORD_LENGTH = 40;
        public static final int MIN_PASSWORD_LENGTH = 3;
        public static final int MAX_USERNAME_LENGTH = 40;
        public static final int MIN_USERNAME_LENGTH = 3;
        private final DBObject nue;
        private final Event evt;
        private final ObjectMapper mapper;

        @Inject
        SignerUpper(DBCollection coll, Event evt, PasswordHasher crypto, ObjectMapper mapper, AuthSupport supp, Publisher publisher) throws UnsupportedEncodingException, IOException {
            this.mapper = mapper;
            this.evt = evt;
            add(Headers.CONTENT_TYPE, MediaType.JSON_UTF_8);

            String userName = URLDecoder.decode(evt.getPath().getElement(1).toString(),
                    "UTF-8");

            nue = new BasicDBObject(name, new String[]{userName})
                    .append(displayName, evt.getParameter(displayName))
                    .append(created, DateTime.now().getMillis())
                    .append(version, 0).append("authorizes", new ObjectId[0]);

            if (!(evt.getRequest() instanceof FullHttpRequest)) {
                if (userName.length() > MAX_USERNAME_LENGTH) {
                    setState(new RespondWith(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                            "Not a " + FullHttpRequest.class.getName()));
                    return;
                }
                setState(new RespondWith(HttpResponseStatus.REQUEST_URI_TOO_LONG,
                        "Max username length " + MAX_USERNAME_LENGTH));
                return;
            }
            if (userName.length() < MIN_USERNAME_LENGTH) {
                setState(new RespondWith(HttpResponseStatus.REQUEST_URI_TOO_LONG,
                        "Minium username length " + MIN_USERNAME_LENGTH));
                return;
            }
            DBObject existing = coll.findOne(new BasicDBObject("name", userName));
            if (existing != null) {
                setState(new RespondWith(HttpResponseStatus.CONFLICT,
                        "A user named '" + userName + "' already exists"));
                return;
            }
            evt.getChannel().read();
            FullHttpRequest req = (FullHttpRequest) evt.getRequest();
            ByteBuf inbound = req.data();
            int count;
            do {
                count = inbound.readableBytes();
                if (count > 0) {
                    inbound.readBytes(out, count);
                }
            } while (count > 0);
            String password = new String(out.toByteArray(), "UTF-8");
            if (password.length() > MAX_PASSWORD_LENGTH) {
                setState(new RespondWith(HttpResponseStatus.BAD_REQUEST,
                        "Password to long (" + password.length()
                        + ")  max " + MAX_PASSWORD_LENGTH));
                return;
            }
            if (password.length() < MIN_PASSWORD_LENGTH) {
                setState(new RespondWith(HttpResponseStatus.BAD_REQUEST,
                        "Password to short (" + password.length()
                        + ")  min " + MIN_PASSWORD_LENGTH));
                return;
            }
            String encrypted = crypto.encryptPassword(password);
            nue.put(pass, encrypted);
            nue.put(origPass, encrypted);
            WriteResult res = coll.save(nue, WriteConcern.FSYNCED);
//            if (res.getN() != 0) {
                Cookie key = supp.encodeDisplayNameCookie(evt.getParameter(displayName));
                add(Headers.SET_COOKIE, key);
                setState(new RespondWith(HttpResponseStatus.CREATED));
                setResponseBodyWriter(this);
                
                add(Headers.SET_COOKIE, supp.encodeLoginCookie(nue, userName, encrypted));
                publisher.publish("signup", new BasicDBObject("name", evt.getParameter(displayName)));
//            } else {
//                setState(new RespondWith(HttpResponseStatus.INTERNAL_SERVER_ERROR, "No write"));
//            }
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            Map m = nue.toMap();
            m.remove(pass);
            m.remove(origPass);
            future = future.channel().write(
                    Unpooled.wrappedBuffer(mapper.writeValueAsBytes(m)));
            if (!evt.isKeepAlive()) {
                future.addListener(CLOSE);
            }
        }
    }
}
