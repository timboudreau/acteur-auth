package com.timboudreau.trackerapi;

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
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import com.timboudreau.trackerapi.support.AuthorizedChecker;
import com.timboudreau.trackerapi.support.UserCollectionFinder;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
public class AuthorizeResource extends Page {

    @Inject
    AuthorizeResource(ActeurFactory af) {
        add(af.matchPath("^users/.*?/authorize/.*?"));
        add(af.matchMethods(Method.PUT, Method.POST));
        add(Auth.class);
        add(AuthorizedChecker.class);
        add(UserCollectionFinder.class);
        add(Authorizer.class);
    }

    @Override
    protected String getDescription() {
        return "Authorize another user to access my data";
    }

    private static final class Authorizer extends Acteur {

        @Inject
        Authorizer(TTUser user, Event evt, DBCollection coll) throws URISyntaxException, UnsupportedEncodingException {
            String otherUserNameOrID = evt.getPath().getElement(3).toString();
            otherUserNameOrID = URLDecoder.decode(otherUserNameOrID, "UTF-8");
            BasicDBObject findOtherUserQuery = new BasicDBObject("name", otherUserNameOrID);
            DBObject otherUser = coll.findOne(findOtherUserQuery);
            if (otherUser == null) {
                try {
                    findOtherUserQuery = new BasicDBObject("_id", new ObjectId(otherUserNameOrID));
                } catch (IllegalArgumentException ex) {
                    setState(new RespondWith(HttpResponseStatus.BAD_REQUEST, 
                            "Cannot parse '" + otherUserNameOrID 
                            + "' as an ID and it does not match any user name"));
                    return;
                }
                otherUser = coll.findOne(findOtherUserQuery);
            }
            if (otherUser == null) {
                setState(new RespondWith(HttpResponseStatus.GONE, "No such user " + otherUserNameOrID));
                return;
            }
            BasicDBObject query = new BasicDBObject("_id", user.id());
            BasicDBObject update = new BasicDBObject("$addToSet", new BasicDBObject(Properties.authorizes, otherUser.get("_id")));
            BasicDBObject inc = new BasicDBObject("version", 1);
            update.append("$inc", inc);
            
            WriteResult res = coll.update(query, update, false, false, WriteConcern.FSYNCED);
            HttpResponseStatus status = HttpResponseStatus.ACCEPTED;
            if (evt.getParameter("redir") != null) {
                URI uri = new URI(evt.getParameter("redir"));
                add(Headers.LOCATION, uri);
                status = HttpResponseStatus.SEE_OTHER;
            }
            setState(new RespondWith(status, Timetracker.quickJson("updated", res.getN())));
        }
    }
}
