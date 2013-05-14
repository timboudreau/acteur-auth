package com.mastfrog.acteur.auth;

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.auth.HomePageRedirector.DefaultHomePageRedirector;

/**
 * Provides a "home page" redirect URI - returned by TestLoginPage. This can
 * clue the UI to send the user to a different page if they are already logged
 * in and land on the home page. The default implementation just returns /.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultHomePageRedirector.class)
public abstract class HomePageRedirector {

    public abstract <T> String getRedirectURI(UserFactory<T> uf, T user, Event evt);

    static class DefaultHomePageRedirector extends HomePageRedirector {

        @Override
        public <T> String getRedirectURI(UserFactory<T> uf, T user, Event evt) {
            return "/";
        }
    }
}
