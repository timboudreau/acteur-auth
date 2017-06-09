package com.timboudreau.trackerapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.OAuthPlugins;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
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
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;

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
        private final HttpEvent evt;
        private final ObjectMapper mapper;

        @Inject
        SignerUpper(DBCollection coll, HttpEvent evt, PasswordHasher crypto, ObjectMapper mapper, OAuthPlugins pgns, Publisher publisher) throws UnsupportedEncodingException, IOException {
            this.mapper = mapper;
            this.evt = evt;
            add(Headers.CONTENT_TYPE, MediaType.JSON_UTF_8);

            String userName = URLDecoder.decode(evt.path().getElement(1).toString(),
                    "UTF-8");

            nue = new BasicDBObject(name, new String[]{userName})
                    .append(displayName, evt.urlParameter(displayName))
                    .append(created, DateTime.now().getMillis())
                    .append(version, 0).append("authorizes", new ObjectId[0]);

            if (!(evt.request() instanceof FullHttpRequest)) {
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
            evt.channel().read();
            FullHttpRequest req = (FullHttpRequest) evt.request();
            ByteBuf inbound = req.content();
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
            pgns.createDisplayNameCookie(evt, response(), displayName);
            setState(new RespondWith(HttpResponseStatus.CREATED));
            Map<String, Object> m = nue.toMap();
            m.remove(pass);
            m.remove(origPass);
            m.remove("slugs");
            setState(new RespondWith(CREATED, m));

            publisher.publish("signup", new BasicDBObject("name", evt.urlParameter(displayName)));
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
            if (!evt.requestsConnectionStayOpen()) {
                future.addListener(CLOSE);
            }
        }
    }
}
