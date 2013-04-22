package com.timboudreau.trackerapi.support;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Event;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.Path;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.timboudreau.trackerapi.Properties;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URLDecoder;
import java.util.List;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
public class AuthorizedChecker extends Acteur {

    @Inject
    AuthorizedChecker(Event evt, TTUser user, DB db, Settings settings) {
        Path pth = evt.getPath();
        String userNameInURL = pth.getElement(1).toString();
        userNameInURL = URLDecoder.decode(userNameInURL);
        if (pth.size() >= 2 && "users".equals(pth.getElement(0).toString())) {
            if (!user.name.equals(userNameInURL)) {
                DBObject query = new BasicDBObject(Properties.name, userNameInURL);
                DBCollection coll = db.getCollection(settings.getString("users.collection.name", "ttusers"));
                boolean authorized = false;
                DBObject otherUser = coll.findOne(query);
                if (otherUser != null) {
                    List<ObjectId> ids = (List<ObjectId>) otherUser.get(Properties.authorizes);
                    System.out.println("GOT BAKC " + ids);
                    if (ids != null) {
                        if (ids.contains(user.id)) {
                            authorized = true;
                        }
                    }
                }
                if (!authorized) {
                    setState(new RespondWith(HttpResponseStatus.FORBIDDEN, user.name
                            + " not allowed access to data belonging to " + userNameInURL + "\n"));
                    return;
                }
            }
        }
        setState(new ConsumedState());
    }
}
