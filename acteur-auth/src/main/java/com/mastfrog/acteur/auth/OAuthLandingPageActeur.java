package com.mastfrog.acteur.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.auth.OAuthPlugin.RemoteUserInfo;
import com.mastfrog.acteur.auth.UserFactory.LoginState;
import com.mastfrog.acteur.auth.UserFactory.Slug;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.Host;
import com.mastfrog.url.Path;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Map;
import org.openide.util.NbCollections;

/**
 *
 * @author Tim Boudreau
 */
final class OAuthLandingPageActeur extends Acteur {

    private final OAuthPlugins plugins;
    private final HomePageRedirector redir;
    private final ObjectMapper mapper;

    @Inject
    OAuthLandingPageActeur(Event evt, ObjectMapper mapper, OAuthPlugins plugins, UserFactory<?> users, Settings settings, HomePageRedirector redir) throws URISyntaxException, IOException {
        this.redir = redir;
        this.plugins = plugins;
        this.mapper = mapper;

        Path base = Path.parse(plugins.getLandingPageBasePath());

        // The URL should be in the form $BASE/$CODE
        // For cases such as Twitter, which does not send state as part of the
        // URL, we encode it as the last path parameter
        String pluginType = evt.getPath().getElement(base.size()).toString();
        // Try to find a plugin matching this code
        Optional<OAuthPlugin<?>> plugino = plugins.find(pluginType);
        
        if (!plugino.isPresent()) {
            setState(new RespondWith(HttpResponseStatus.BAD_REQUEST,
                    "No plugin with code " + pluginType + " in " + plugins));
            return;
        }
        OAuthPlugin<?> plugin = plugino.get();
        // Get the random string we sent to the OAuth service, which identifies
        // valid requests
        String state = plugin.stateForEvent(evt);
        if (evt.getPath().size() > base.size() + 1) {
            state = evt.getPath().getLastElement().toString();
        }
        if (state == null) {
            setState(new RespondWith(HttpResponseStatus.BAD_REQUEST, "No state in url " + evt.getRequest()));
            return;
        }
        // Look it up and make sure it's legitimate - if not, someone might be
        // sending random stuff and hoping to get lucky
        Optional<LoginState> stateo = users.lookupLoginState(state);
        if (!stateo.isPresent()) {
            setState(new RespondWith(HttpResponseStatus.BAD_REQUEST, "Bogus login state " + state));
            return;
        }
        LoginState st = stateo.get();
        if (st.used) {
            setState(new RespondWith(HttpResponseStatus.BAD_REQUEST, "Already used credential " + state));
            return;
        }
        finish(plugin, evt, users, stateo.get());
    }

    private Map<String, Object> toMap(RemoteUserInfo info) throws JsonProcessingException, IOException {
        if (info instanceof Map) {
            return NbCollections.checkedMapByCopy(info, String.class, Object.class, false);
        }
        String s = mapper.writeValueAsString(info);
        return mapper.readValue(s, Map.class);
    }

    private <T, R> void finish(OAuthPlugin<T> plugin, Event evt, UserFactory<R> users, LoginState state) throws URISyntaxException, IOException {
        // Get the plugin, such as a GoogleCredential
        T credential = plugin.credentialForEvent(evt);
        // Connect to the remote service and get enough information to create
        // or login a user
        RemoteUserInfo rui = plugin.getRemoteUserInfo(credential);
        // No info?  Something wrong here
        if (rui == null) {
            setState(new RespondWith(HttpResponseStatus.BAD_REQUEST, "Remote says no user for " + credential));
            return;
        }

        // Now try to look up the user
        Optional<R> op = users.findUserByName(rui.userName());
        R user = null;
        Slug slug;
        if (op.isPresent()) {
            user = op.get();
            // Get an existing slug (another random string) for this service
            // to encode into a cookie
            slug = users.getSlug(plugin.code(), user, true).get();
            // If the slug is expired, create a new one
            if (slug.age().isLongerThan(plugin.getSlugMaxAge())) {
                // Create a new slug
                slug = users.newSlug(plugin.code());
                // Overwrite the old one
                users.putSlug(user, slug);
                users.putData(user, plugin.code(), toMap(rui));
                plugin.saveToken(users, user, credential);
            }
        } else {
            // Create a new slug for the new user
            slug = users.newSlug(plugin.code());
            // Create a new user
            user = users.newUser(rui.userName(), slug, rui.displayName(), rui, plugin);
            users.putData(user, plugin.code(), toMap(rui));
            plugin.saveToken(users, user, credential);
        }
        // Encode the slug into a cookie - this hashes the slug (which is a random
        // string anyway) with a salt and the user name
        String cookieValue = plugins.encodeCookieValue(rui.userName(), slug.slug);
        DefaultCookie ck = new DefaultCookie(plugin.code(), cookieValue);
        
        Host host = plugins.cookieHost() == null ? evt.getHeader(Headers.HOST) : Host.parse(plugins.cookieHost());
        if (host == null) {
            // If we can't figure out our own host, we're hosed - the cookie
            // won't be saved anyway
            host = Host.parse("fail.example");
        }
        ck.setDomain(host.toString());
        ck.setPorts(plugins.cookiePorts());
        ck.setMaxAge(plugin.getSlugMaxAge().getMillis());
        ck.setPath(plugins.cookieBasePath());
        add(Headers.SET_COOKIE, ck);

        plugins.createDisplayNameCookie(evt, response(), rui.displayName());

        // See if the request has a redirect already - we may have passed one
        // to the remote service and it is passing it back to us
        String redirTo = state.redirectTo;
        if (redirTo != null) {
            URI uri = new URI(URLDecoder.decode(redirTo, "UTF-8"));
            add(Headers.LOCATION, uri);
            // Do the redirect
            setState(new RespondWith(HttpResponseStatus.FOUND, "Logged in " + rui.displayName() + " (" + rui.userName() + ")"));
        } else {
            // If not, look up the default, which is set in settings and defaults to /
            URI uri = new URI(redir.getRedirectURI(users, user, evt));
            add(Headers.LOCATION, uri);
            // Do the redirect
            setState(new RespondWith(HttpResponseStatus.FOUND, "Logged in " + rui.displayName() + " (" + rui.userName() + ")"));
        }
    }
}
