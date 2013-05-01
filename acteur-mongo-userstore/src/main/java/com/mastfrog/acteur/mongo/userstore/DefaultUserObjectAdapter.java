package com.mastfrog.acteur.mongo.userstore;

import com.mongodb.DBObject;

/**
 *
 * @author tim
 */
final class DefaultUserObjectAdapter implements UserObjectAdapter {

    @Override
    public Object toUserObject(DBObject user) {
        return new TTUser(user);
    }

    @Override
    public DBObject toUserObject(Object user) {
        return ((TTUser) user).obj;
    }

}
