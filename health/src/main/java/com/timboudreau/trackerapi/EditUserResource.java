package com.timboudreau.trackerapi;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.AuthenticationActeur;
import com.mastfrog.acteur.auth.OAuthPlugins;
import com.mastfrog.acteur.mongo.userstore.TTUser;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.util.PasswordHasher;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import com.timboudreau.trackerapi.support.AuthorizedChecker;
import com.timboudreau.trackerapi.support.UserCollectionFinder;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.net.URLDecoder;
import org.joda.time.DateTimeUtils;

/**
 *
 * @author Tim Boudreau
 */
public class EditUserResource extends Page {

    @Inject
    EditUserResource(ActeurFactory af) {
        add(af.matchPath("^users/([^/]*?)/?$"));
        add(af.matchMethods(Method.PUT, Method.POST));
        add(af.banParameters(Properties._id, Properties.name, Properties.pass, Properties.origPass, Properties.authorizes));
        add(af.requireAtLeastOneParameter("displayName"));
        add(AuthenticationActeur.class);
        add(AuthorizedChecker.class);
        add(UserCollectionFinder.class);
        add(UpdateUserActeur.class);
    }

    private static class UpdateUserActeur extends Acteur {

        @Inject
        UpdateUserActeur(DBCollection coll, HttpEvent evt, PasswordHasher hasher, TTUser user, OAuthPlugins pgns) throws IOException {
            String userName = URLDecoder.decode(evt.getPath().getElement(1).toString(), "UTF-8");
            String dn = evt.getParameter(Properties.displayName);

            if (!userName.equals(user.name())) {
                setState(new RespondWith(HttpResponseStatus.FORBIDDEN, user.name()
                        + " cannot set the password for " + userName));
                return;
            }
            if (dn.length() < SignUpResource.SignerUpper.MIN_USERNAME_LENGTH) {
                badRequest("Display name '"+ dn 
                        + "' too short - min is " 
                        + SignUpResource.SignerUpper.MIN_USERNAME_LENGTH);
                return;
            }
            if (dn.length() > SignUpResource.SignerUpper.MAX_USERNAME_LENGTH) {
                badRequest("Display name too long - max is " 
                        + SignUpResource.SignerUpper.MAX_USERNAME_LENGTH);
                return;
            }
            if (dn.equals(user.displayName())) {
                ok(Timetracker.quickJson("updated", 0));
                return;
            }

            DBObject query = coll.findOne(new BasicDBObject("name", userName));

            DBObject update = new BasicDBObject("$set", new BasicDBObject("displayName", dn)
                    .append("lastModified", DateTimeUtils.currentTimeMillis())).append("$inc",
                    new BasicDBObject("version", 1));

            WriteResult res = coll.update(query, update, false, false, WriteConcern.FSYNCED);
            if (res.getN() == 1) {
                pgns.createDisplayNameCookie(evt, response(), dn);
            }

            reply(HttpResponseStatus.ACCEPTED, Timetracker.quickJson("updated", res.getN()));
        }
    }
}
