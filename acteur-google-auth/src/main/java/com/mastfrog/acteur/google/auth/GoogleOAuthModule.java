package com.mastfrog.acteur.google.auth;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.inject.AbstractModule;

/**
 * Plugs in support for OAuth login through Google.  To use it, a few settings
 * <b>must be set</b> and do not have defaults.  These are the google client secret 
 * and client id.  Set these in your settings file (typically 
 * <code>/etc/$APP_NAME.properties</code> or <code>~/$APP_NAME.properties</code>.
 *
 * @author Tim Boudreau
 */
public class GoogleOAuthModule extends AbstractModule {
    /**
     * Settings key for the "scope" parameters to Google when requesting
     * authorization.
     * <b>Do not override</b> unless Google changes their service URLs (very
     * unlikely) or you want to test against a mock service. The defaults are
     * usually what you want for this. At a minimum, this list neds to contain
     * <code>https://www.googleapis.com/auth/userinfo.email</code> and
     * <code>https://www.googleapis.com/auth/userinfo.profile</code> in order to
     * get enough information to create a record of a user - things will go
     * badly wrong if these don't work.
     * <p/>
     * This setting is a comma-delimited list of URLs; leading or trailing
     * whitespace is okay.
     * <p/>
     * If you just want to <i>add</i> some scopes for the application to use for
     * its own purposes, use
     * <code>SETTINGS_KEY_ADDITIONAL_SCOPES</code> (whose value is
     * <code>oauth.google.additional.scopes</code> instead).
     */
    public static final String SETTINGS_KEY_SCOPES = "oauth.google.scopes";
    /**
     * Settings key for additional "scopes" to request authorization for from
     * Google - a scope is generally an application or a kind of data you want
     * to access.
     * <p/>
     * The total set of scopes access is requested for is a combination of this
     * setting and the defaults defined above.
     * <p/>
     * This setting is a comma-delimited list of URLs; leading or trailing
     * whitespace is okay.
     */
    public static final String SETTINGS_KEY_ADDITIONAL_SCOPES = "oauth.google.additional.scopes";

    /**
     * The Google client id, which you were given when you registered with Google
     * (you did that, didn't you?).
     */
    public static final String SETTINGS_KEY_GOOGLE_CLIENT_ID = "google.client.id";
    /**
     * The Google client secret which identifies your app to Google.
     */
    public static final String SETTINGS_KEY_GOOGLE_CLIENT_SECRET = "google.client.secret";
    final NetHttpTransport transport = new NetHttpTransport();
    final JacksonFactory factory = new JacksonFactory();

    @Override
    protected void configure() {
        bind(GoogleOAuthPlugin.class).asEagerSingleton();
        bind(HttpTransport.class).toInstance(transport);
        bind(JacksonFactory.class).toInstance(factory);
    }
    
}
