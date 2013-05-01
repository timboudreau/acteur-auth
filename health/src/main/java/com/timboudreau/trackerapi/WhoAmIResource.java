package com.timboudreau.trackerapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.Auth;
import com.mastfrog.acteur.mongo.userstore.TTUser;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.util.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.timboudreau.trackerapi.support.AuthSupport;
import com.timboudreau.trackerapi.support.UserCollectionFinder;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
public class WhoAmIResource extends Page {

    @Inject
    WhoAmIResource(ActeurFactory af) {
        add(af.matchPath("^whoami/?$"));
        add(af.matchMethods(Method.GET));
        add(Auth.class);
        add(UserCollectionFinder.class);
        add(UserInfoActeur.class);
    }

    @Override
    protected String getDescription() {
        return "Authenticate login and fetch user name";
    }

    private static class UserInfoActeur extends Acteur {

        @Inject
        UserInfoActeur(TTUser user, DBCollection coll, ObjectMapper mapper, AuthSupport supp, Event evt) throws IOException {
            boolean other = evt.getParameter("user") != null && !user.names().contains(evt.getParameter("user"));
            add(Headers.stringHeader("UserID"), user.id().toStringMongod());
            DBObject ob = other ? coll.findOne(new BasicDBObject("name", evt.getParameter("user")), 
                    new BasicDBObject("_id", 1).append("name", 1).append("displayName", 1)) 
                    : coll.findOne(new BasicDBObject("_id", user.id()));
            if (ob == null) {
                setState(new RespondWith(HttpResponseStatus.GONE, "No record of " + user.name()));
                return;
            }
            Map<String, Object> m = new HashMap<>(ob.toMap());
            m.remove(Properties.pass);
            m.remove(Properties.origPass);
            m.remove("cookieSlug");
            String dn = (String) m.get(Properties.displayName);
            if (dn != null && !other) {
                add(Headers.SET_COOKIE, supp.encodeDisplayNameCookie(dn));
            }
            setState(new RespondWith(200, mapper.writeValueAsString(m)));
        }
    }
}
