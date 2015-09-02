package com.timboudreau.trackerapi.support;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.mongo.userstore.TTUser;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.timboudreau.trackerapi.Properties;

/**
 *
 * @author Tim Boudreau
 */
public final class TimeCollectionFinder extends Acteur {

    @Inject
    TimeCollectionFinder(Provider<DB> db, HttpEvent evt, TTUser u, CreateCollectionPolicy create) {
        Provider<DBCollection> coll;
        String userNameInURL = evt.getPath().getElement(1).toString();

        String category = evt.getPath().getElement(3).toString();
        String collectionName = new StringBuilder(userNameInURL).append('_').append(category).toString().intern();
        if (!db.get().collectionExists(collectionName)) {
            switch (create) {
                case CREATE:
                    coll = new CollectionProvider(db, collectionName, true);
                    break;
                default:
                    notFound("No collection " + collectionName);
                    return;
            }
        } else {
            coll = new CollectionProvider(db, collectionName, false);
        }
        next(coll);
    }

    private static class CollectionProvider implements Provider<DBCollection> {

        private final Provider<DB> db;
        private final String collectionName;
        private boolean first;
        private final boolean ensureIndexes;

        public CollectionProvider(Provider<DB> db, String collectionName, boolean ensureIndexes) {
            this.ensureIndexes = ensureIndexes;
            this.db = db;
            this.collectionName = collectionName;
        }

        @Override
        public DBCollection get() {
            DBCollection coll = db.get().getCollection(collectionName);
            if (first && ensureIndexes) {
                first = false;
                BasicDBObject q = new BasicDBObject(Properties.start, 1).append(Properties.end, 1);
                coll.createIndex(q, collectionName + "_startEnd", first);
                q = new BasicDBObject("tags", 1);
                coll.createIndex(q, new BasicDBObject("sparse", true).append("name", "tags_index"));
            }
            return coll;
        }
    }
}
