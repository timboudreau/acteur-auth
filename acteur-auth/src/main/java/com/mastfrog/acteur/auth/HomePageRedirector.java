package com.mastfrog.acteur.auth;

import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.auth.HomePageRedirector.DefaultHomePageRedirector;
import com.mastfrog.settings.Settings;

/**
 * Provides a "home page" redirect URI - returned by TestLoginPage. This can
 * clue the UI to send the user to a different page if they are already logged
 * in and land on the home page. The default implementation just returns /.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultHomePageRedirector.class)
public abstract class HomePageRedirector {
    public static final String SETTINGS_KEY_OAUTH_LANDING_PAGE_REDIRECT = "oauth.landing.redirect";

    public abstract <T> String getRedirectURI(UserFactory<T> uf, T user, Event evt);

    static class DefaultHomePageRedirector extends HomePageRedirector {
        private final String path;
        @Inject
        public DefaultHomePageRedirector(Settings settings) {
            this.path = settings.getString(SETTINGS_KEY_OAUTH_LANDING_PAGE_REDIRECT, "/");
        }

        @Override
        public <T> String getRedirectURI(UserFactory<T> uf, T user, Event evt) {
            return path;
        }
    }
}
