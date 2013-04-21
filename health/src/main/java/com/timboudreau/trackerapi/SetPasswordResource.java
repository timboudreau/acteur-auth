package com.timboudreau.trackerapi;

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
import com.timboudreau.trackerapi.support.Auth;
import com.timboudreau.trackerapi.support.AuthSupport;
import com.timboudreau.trackerapi.support.AuthorizedChecker;
import com.timboudreau.trackerapi.support.TTUser;
import com.timboudreau.trackerapi.support.UserCollectionFinder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.ServerCookieEncoder;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 *
 * @author Tim Boudreau
 */
public class SetPasswordResource extends Page {

    @Inject
    SetPasswordResource(ActeurFactory af) {
        add(af.matchPath("^users/(.*?)/password$"));
        add(af.matchMethods(Method.PUT, Method.POST));
        add(Auth.class);
        add(AuthorizedChecker.class);
        add(UserCollectionFinder.class);
        add(SetPasswordActeur.class);
    }

    static class SetPasswordActeur extends Acteur {

        @Inject
        SetPasswordActeur(DBCollection coll, Event evt, PasswordHasher hasher, AuthSupport supp, TTUser user) throws IOException {
            String userName = evt.getPath().getElement(1).toString();
            String pw = evt.getContent().toString(Charset.forName("UTF-8"));
            if (pw.length() < SignUpResource.SignerUpper.MIN_PASSWORD_LENGTH) {
                setState(new RespondWith(400, "Password too short"));
                return;
            }
            if (pw.length() >= SignUpResource.SignerUpper.MAX_PASSWORD_LENGTH) {
                setState(new RespondWith(400, "Password too long"));
                return;
            }
            if (!userName.equals(user.name)) {
                setState(new RespondWith(HttpResponseStatus.FORBIDDEN, user.name 
                        + " cannot set the password for " + userName));
                return;
            }

            System.out.println("Set password for " + userName + " to " + pw);

            String hashed = hasher.encryptPassword(pw.toString());

            DBObject query = coll.findOne(new BasicDBObject("name", userName));

            DBObject update = new BasicDBObject("$set", new BasicDBObject("pass", hashed)).append("$inc", 
                    new BasicDBObject("version", 1));

            WriteResult res = coll.update(query, update, false, false, WriteConcern.FSYNCED);
            
            String ck = supp.encodeLoginCookie(query, user.name, hashed);
            add(Headers.SET_COOKIE, ck);

//            setState(new RespondWith(200, Timetracker.quickJson("updated", res.getN())));
            setState(new RespondWith(200, "Password updated."));
        }
    }
}
