package com.timboudreau.trackerapi;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.Auth;
import com.mastfrog.acteur.mongo.userstore.TTUser;
import com.mastfrog.acteur.util.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import com.timboudreau.trackerapi.support.AuthorizedChecker;
import com.timboudreau.trackerapi.support.UserCollectionFinder;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
public class DeauthorizeResource extends Page {

    @Inject
    DeauthorizeResource(ActeurFactory af) {
        add(af.matchPath("^users/.*?/deauthorize/.*?"));
        add(af.matchMethods(Method.PUT, Method.POST));
        add(Auth.class);
        add(AuthorizedChecker.class);
        add(UserCollectionFinder.class);
        add(Authorizer.class);
    }

    @Override
    protected String getDescription() {
        return "Remove another user's authorization to use my data";
    }

    private static final class Authorizer extends Acteur {

        @Inject
        Authorizer(TTUser user, Event evt, DBCollection coll) throws UnsupportedEncodingException {
            String otherUserNameOrID = evt.getPath().getElement(3).toString();
            otherUserNameOrID = URLDecoder.decode(otherUserNameOrID, "UTF-8");
            BasicDBObject findOtherUserQuery = new BasicDBObject("name", otherUserNameOrID);
            DBObject otherUser = coll.findOne(findOtherUserQuery);
            if (otherUser == null) {
                try {
                    findOtherUserQuery = new BasicDBObject("_id", new ObjectId(otherUserNameOrID));
                } catch (IllegalArgumentException e) {
                    setState(new RespondWith(HttpResponseStatus.GONE, "No such user id or name" + otherUserNameOrID));
                    return;
                }
                otherUser = coll.findOne(findOtherUserQuery);
            }
            if (otherUser == null) {
                setState(new RespondWith(HttpResponseStatus.GONE, "No such user " + otherUserNameOrID));
                return;
            }
            BasicDBObject query = new BasicDBObject("_id", user.id());
            BasicDBObject update = new BasicDBObject("$pull", new BasicDBObject(Properties.authorizes, otherUser.get("_id")));
            BasicDBObject inc = new BasicDBObject("version", 1);
            update.append("$inc", inc);
            WriteResult res = coll.update(query, update, false, false, WriteConcern.FSYNCED);
            setState(new RespondWith(HttpResponseStatus.ACCEPTED, Timetracker.quickJson("updated", res.getN())));
        }
    }
}
