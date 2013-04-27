package com.mastfrog.acteur.auth;

/**
 *
 * @author tim
 */
public abstract class UserFactory<T> {
    
    public abstract T findUserByName(String name);
    public abstract String getPasswordHash(T user);
    public abstract void setPasswordHash(T on, String hash);
    public abstract String get(T on, String key);
    public abstract String set(T on, String key);
}
