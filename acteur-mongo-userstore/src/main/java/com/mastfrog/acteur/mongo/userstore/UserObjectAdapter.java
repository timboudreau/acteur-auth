package com.mastfrog.acteur.mongo.userstore;

import com.google.inject.ImplementedBy;
import com.mongodb.DBObject;

/**
 * Adapts a MongoDB DBObject into some type known to the application that can be
 * injected
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultUserObjectAdapter.class)
public interface UserObjectAdapter {

    public Object toUserObject(DBObject user);

    public DBObject toUserObject(Object user);
}
