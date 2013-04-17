package com.timboudreau.trackerapi.support;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.util.BasicCredentials;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.util.PasswordHasher;
import com.mastfrog.acteur.util.Realm;
import com.mastfrog.settings.Settings;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.timboudreau.trackerapi.Properties;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.bson.types.ObjectId;
import static com.timboudreau.trackerapi.Properties.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Handles authentication
 *
 * @author Tim Boudreau
 */
public final class Auth extends Acteur {
    private final Realm realm;

    @Inject
    Auth(Event evt, DB db, PasswordHasher crypto, Settings settings, Realm realm) {
        this.realm = realm;
        String userCollectionName = settings.getString("users.collection.name", "ttusers");
        BasicCredentials c = evt.getHeader(Headers.AUTHORIZATION);
        if (c == null) {
            add(Headers.WWW_AUTHENTICATE, realm);
            setState(new RespondWith(HttpResponseStatus.UNAUTHORIZED, "No."));
            return;
        }
        DBCollection coll = db.getCollection(userCollectionName);
        DBObject u = coll.findOne(new BasicDBObject(name, new String[]{c.username}));
        if (u == null) {
            u = coll.findOne(new BasicDBObject(name, c.username));
        }
        if (u == null) {
            add(Headers.WWW_AUTHENTICATE, realm);
            setState(new RespondWith(HttpResponseStatus.UNAUTHORIZED, "No user " + c.username + '\n'));
            return;
        }
        String pw = (String) u.get(pass);
        if (pw == null) {
            setState(new RespondWith(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Password record missing\n"));
            return;
        }
        if (!crypto.checkPassword(c.password, pw)) {
            add(Headers.WWW_AUTHENTICATE, realm);
            setState(new RespondWith(HttpResponseStatus.UNAUTHORIZED, "Bad password for " + c.username + '\n'));
            return;
        }
        List<ObjectId> authorizes = (List<ObjectId>) u.get("authorizes");
        Number ver = (Number) u.get(Properties.version);
        int version = ver == null ? 0 : ver.intValue();

        setState(new ConsumedLockedState(new TTUser(c.username, (ObjectId) u.get("_id"),
                version, authorizes)));
    }

    @Override
    public void describeYourself(Map<String, Object> into) {
        into.put("Requires Authentication", Collections.singletonMap("realm", realm.toString()));
    }
}
