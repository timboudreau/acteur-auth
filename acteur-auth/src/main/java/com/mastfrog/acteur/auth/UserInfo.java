package com.mastfrog.acteur.auth;

/**
 *
 * @author tim
 */
public final class UserInfo {

    public final String userName;
    public final String hashedSlug;

    UserInfo(String userName, String hashedSlug) {
        this.userName = userName;
        this.hashedSlug = hashedSlug;
    }

}
