package com.timboudreau.trackerapi;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.mongo.CursorWriter;
import com.mastfrog.acteur.mongo.CursorWriterActeur;
import com.mastfrog.acteur.headers.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.timboudreau.trackerapi.support.UserCollectionFinder;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.timboudreau.trackerapi.Properties.*;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
class ListUsersResource extends Page {

    @Inject
    ListUsersResource(ActeurFactory af) {
        add(af.matchMethods(false, Method.GET, Method.HEAD));
        add(af.matchPath("^all$"));
        add(UserCollectionFinder.class);
        add(UF.class);
        add(CursorWriterActeur.class);
    }

    @Override
    protected String getDescription() {
        return "List all users";
    }

    static class UF extends Acteur {

        final DBCursor cursor;
        final AtomicBoolean first = new AtomicBoolean(true);

        @Inject
        UF(DBCollection coll, HttpEvent evt) {
            final boolean simple = "true".equals(evt.urlParameter("simple"));
            cursor = simple ? coll.find(new BasicDBObject(), new BasicDBObject("_id", 1).append("name", 1).append("displayName", 1).append("created", 1)) : coll.find();
            setState(new ConsumedLockedState(cursor, new CursorWriter.MapFilter() {

                @Override
                public Map<String, Object> filter(Map<String, Object> m) {
                    if (!simple) {
                        m.remove(pass);
                        m.remove(origPass);
                        m.remove("slugs");
                        m.remove("tokens");
                        Object i = m.get("_id");
                        if (i instanceof ObjectId) {
                            i = ((ObjectId) i).toStringMongod();
                            m.put("_id", i);
                        }
                    }
                    return m;
                }
            }));
        }
    }
}
