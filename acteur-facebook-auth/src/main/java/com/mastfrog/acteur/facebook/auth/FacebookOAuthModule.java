package com.mastfrog.acteur.facebook.auth;

import com.google.inject.AbstractModule;

/**
 * Plugs in support for OAuth login through Google.  To use it, a few settings
 * <b>must be set</b> and do not have defaults.  These are the google client secret 
 * and client id.  Set these in your settings file (typically 
 * <code>/etc/$APP_NAME.properties</code> or <code>~/$APP_NAME.properties</code>.
 *
 * @author Tim Boudreau
 */
public class FacebookOAuthModule extends AbstractModule {
    /**
     * The Google client id, which you were given when you registered with Google
     * (you did that, didn't you?).
     */
    public static final String SETTINGS_KEY_FACEBOOK_APP_ID = "facebook.app.id";
    /**
     * The Google client secret which identifies your app to Google.
     */
    public static final String SETTINGS_KEY_FACEBOOK_APP_SECRET = "facebook.app.secret";

    @Override
    protected void configure() {
        bind(FacebookOAuthPlugin.class).asEagerSingleton();
    }
    
}
