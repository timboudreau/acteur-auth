package com.timboudreau.trackerapi.support;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.settings.Settings;
import com.mongodb.DB;
import com.mongodb.DBCollection;

/**
 * Finds the MongoDB collection representing the list of users.
 *
 * @author Tim Boudreau
 */
public final class UserCollectionFinder extends Acteur {

    @Inject
    UserCollectionFinder(DB db, Settings settings) {
        String userCollectionName = settings.getString("user.collection.name", "ttusers");
        DBCollection coll = db.getCollection(userCollectionName);
        next(coll);
    }

}
