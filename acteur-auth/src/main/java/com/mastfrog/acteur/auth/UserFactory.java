package com.mastfrog.acteur.auth;

/**
 *
 * @author tim
 */
public abstract class UserFactory<T> {
    
    public abstract T findUserByName(String name);
}
