package com.mastfrog.acteur.auth;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.auth.UserFactory.LoginState;
import com.mastfrog.acteur.auth.UserFactory.Slug;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.util.PasswordHasher;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.strings.Strings;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * General-purpose Acteur for use on "Login with Service" URLs. This does not
 * attempt to handle the vagaries of different oauth versions or
 * implementations, just make it so that you don't need to implement anything
 * but that part.
 * <p/>
 * When a user first logs in via an oauth service, we generate a random unique
 * "slug" value and store that with the user, together with a current timestamp
 * and the name of the service in question. And we generate a unique string
 * which we can store in temporary storage (such as a mongodb capped collection)
 * along with potentially a redirect destination, which is used as the "state"
 * for the oauth callback page.
 * <p/>
 * Upon return, the slug is hashed together with a salt and the user name and
 * put in a cookie, which can be used until it expires to authenticate the user
 * without reconnecting to the oauth service.
 * <p/>
 * You should set the setting
 * <code>authCookieSalt</code> to a non-default value
 *
 * @author Tim Boudreau
 */
final class InitiateOAuthActeur extends Acteur {

    public static final String REDIRECT_ON_SUCCESS_URL_PARAMETER = "redir";
    private final UserFactory users;
    private final HttpEvent evt;
    private final OAuthPlugins plugins;

    @Inject
    @SuppressWarnings("unchecked")
    InitiateOAuthActeur(HttpEvent evt, OAuthPlugins plugins, Settings settings, UserFactory uf, PasswordHasher hasher, Dependencies deps) throws MalformedURLException, URISyntaxException {
        this.users = uf;
        this.evt = evt;
        this.plugins = plugins;
        // We expect the type code - a two letter code identifying the OAuth service
        // we'll call, based on OAuthPlugin.code() - used in the cookie and to store
        // slugs in the db
        String type = evt.path().getLastElement().toString();
        // Find an OAuth plugin matching the URL pattern
        Optional<OAuthPlugin<?>> oplugin = plugins.find(type);
        if (!oplugin.isPresent()) {
            // No plugin?  Bogus URL.  Use 406 for diagnostic purposes.
            setState(new RespondWith(HttpResponseStatus.NOT_ACCEPTABLE, "No oauth plugin for '" + type + "'.  Available: " + Strings.toString(plugins.cookieNames()) + "\n"));
            return;
        }
        OAuthPlugin<?> plugin = oplugin.get();
        // Find a cookie on the request matching the code
        Cookie ck = findCookie(evt, type);
        // No cookie?  The user was never logged in using this service before - 
        // bounce them to the OAuth service's login page
        if (ck == null) {
            doRedirect(plugin);
            return;
        }
        // Parse the cookie
        Optional<UserInfo> info = parseCookie(ck);
        if (!info.isPresent()) {
            // Garbage in the cookie?  Dump them to the oauth service
            doRedirect(plugin);
            return;
        }
        // Try to actually look up the user from the cookie
        tryFindUserAndLogin(uf, info.get(), plugin, evt);
    }

    private <T, R> void tryFindUserAndLogin(UserFactory<T> uf, UserInfo info, OAuthPlugin<R> plugin, HttpEvent evt) throws MalformedURLException, URISyntaxException {
        // Look up the user in the database
        Optional<T> user = uf.findUserByName(info.userName);
        if (!user.isPresent()) {
            // No such user?  Bounce to the oauth service
            doRedirect(plugin);
            return;
        }
        // Get a record attached to this user which is a unique string assigned
        // when they last logged in with this service
        // The age of the slug is stored with the slug, and if it is too old
        // it may be null
        Optional<Slug> slug = uf.getSlug(plugin.code(), user.get(), false);
        if (!slug.isPresent()) {
            // No slug?  Bounce to the oauth provider
            doRedirect(plugin);
            return;
        }
        // Hash the slug together with the user name - if the result matches
        // the cookie, then we just need to check the validity of the access
        // token stored with the user (if there is one)
        String shouldMatchCookie = hashSlug(slug.get().slug, info.userName);
        if (shouldMatchCookie.equals(info.hashedSlug)) {
            tryToRevalidate(user.get(), uf, info, slug.get(), plugin, evt);
            return;
        }
        // If not, we're don
        doRedirect(plugin);
    }

    private <T> void tryToRevalidate(T user, UserFactory<T> uf, UserInfo info, Slug slug, OAuthPlugin plugin, HttpEvent evt) throws MalformedURLException, URISyntaxException {
        // Get the access token stored with the user, if any, from a previous
        // login
        Optional<String> credential = uf.getAccessToken(user, plugin.code());
        if (credential.isPresent()) {
            // Call back the oauth service and revalidate it if need be
            if (plugin.revalidateCredential(info.userName, credential.get())) {
                // Let the call continue
                finish(evt, user);
                return;
            }
        }
        doRedirect(plugin);
    }

    private <T> void finish(HttpEvent evt, T user) throws URISyntaxException {
        // If the original request had a redirect parameter, redirect to that
        String redirTo = evt.urlParameter(REDIRECT_ON_SUCCESS_URL_PARAMETER);
        if (redirTo != null) {
            add(Headers.LOCATION, new URI(redirTo));
            reply(HttpResponseStatus.FOUND);
        } else {
            // Otherwise, assume that a later acteur is handling things from here
            next(user);
        }
    }

    protected String hashSlug(String slug, String userName) {
        return plugins.encodeCookieValue(userName, slug);
    }

    private Optional<UserInfo> parseCookie(Cookie ck) {
        return plugins.decodeCookieValue(ck.value());
    }

    private void doRedirect(OAuthPlugin<?> plugin) throws MalformedURLException, URISyntaxException {
        String redir = evt.urlParameter(REDIRECT_ON_SUCCESS_URL_PARAMETER);
//        if (redir == null) {
//            redir = plugins.loginRedirect().toString();
//        }
        LoginState state = users.newLoginState(redir);

        // Redirects to the oauth service
        add(Headers.LOCATION, new URI(plugin.getRedirectURL(state)));
        setState(new RespondWith(HttpResponseStatus.SEE_OTHER, "Redirecting to " + plugin.name()));
    }

    private Cookie findCookie(HttpEvent evt, String name) {
        Cookie[] cookies = evt.header(Headers.COOKIE_B);
        if (cookies != null) {
            for (Cookie ck : cookies) {
                if (name.equals(ck.name())) {
                    return ck;
                }
            }
        }
        return null;
    }
}
