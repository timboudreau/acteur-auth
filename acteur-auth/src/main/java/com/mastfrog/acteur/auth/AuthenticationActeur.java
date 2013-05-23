package com.mastfrog.acteur.auth;

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.Acteur;

/**
 * Acteur which does authentication.  The default implementation will be found and
 * used in production mode;  to bind mock authentication, create a subclass and
 * bind that to AuthenticationActeur.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(Auth.class)
public abstract class AuthenticationActeur extends Acteur {
    /**
     * Settings key for boolean property determining whether HTTP Basic
     * authentication can be used.
     */
    public static final String SETTINGS_KEY_ENABLE_BASIC_AUTH = "basic.auth";
    /**
     * Settings key for boolean property determining whether Cookies are used to
     * authenticate.
     */
    public static final String SETTINGS_KEY_ENABLE_COOKIE_AUTH = "cookie.auth";
    /**
     * Header which clients can send to suppress the
     * <code>WWW-Authenticate</code> response header.
     */
    public static final String SKIP_HEADER = "X-No-Authenticate";

}
