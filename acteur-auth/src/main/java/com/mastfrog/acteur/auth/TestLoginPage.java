package com.mastfrog.acteur.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import static com.mastfrog.acteur.auth.AuthenticationActeur.SETTINGS_KEY_ENABLE_BASIC_AUTH;
import com.mastfrog.acteur.auth.OAuthPlugins.PluginInfo;
import com.mastfrog.acteur.auth.UserFactory.Slug;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.util.BasicCredentials;
import com.mastfrog.acteur.util.PasswordHasher;
import com.mastfrog.acteur.util.Realm;
import com.mastfrog.settings.Settings;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.joda.time.Duration;

/**
 *
 * @author tim
 */
final class TestLoginPage extends Page {

    @Inject
    public TestLoginPage(Settings settings, ActeurFactory af) {
        String pattern = settings.getString("login.test.page.pattern", "^testLogin$");
        add(af.matchPath(pattern));
        add(af.matchMethods(Method.GET, Method.POST, Method.PUT));
        add(TestLoginActeur.class);
    }

    @Override
    protected String getDescription() {
        return "Allows a client to determine if it is already logged in "
                + "as one or more users.  URL parameter failwith can be an HTTP "
                + "response code to give if login fails;  URL parameter "
                + "logout=true will log the user out (if using cookie-based "
                + "authentication).";
    }

    static final class TestLoginActeur extends Acteur {

        private final OAuthPlugins plugins;
        private final HomePageRedirector redir;

        @Inject
        TestLoginActeur(HttpEvent evt, OAuthPlugins plugins, AuthenticationStrategy auth, UserFactory<?> uf, Realm realm, HomePageRedirector redir, Settings settings, PasswordHasher hasher) {
            this.plugins = plugins;
            this.redir = redir;
            int code = OK.code();
            if (evt.getParameter("failwith") != null) {
                try {
                    code = Integer.parseInt(evt.getParameter("failwith"));
                } catch (NumberFormatException n) {
                    setState(new RespondWith(400, "Not a number: '" + evt.getParameter("failwith")));
                    return;
                }
            }
            if ("true".equals(evt.getParameter("logout"))) {
                plugins.logout(evt, response());
                HttpResponseStatus status = HttpResponseStatus.NO_CONTENT;
                if (evt.getHeader(Headers.AUTHORIZATION) != null) {
                    // Force the browser to think its stored credentials
                    // are invalid if using basic auth
                    status = HttpResponseStatus.UNAUTHORIZED;
                    add(Headers.WWW_AUTHENTICATE, realm);
                }
                setState(new RespondWith(status));
                return;
            }
            Cookie[] ck = evt.getHeader(Headers.COOKIE);
            Map<String, Cookie> cookieForName = new HashMap<>();
            Result result = new Result();
            if (ck != null && ck.length > 0) {
                for (Cookie c : ck) {
                    cookieForName.put(c.getName(), c);
                }
                for (PluginInfo info : plugins.getPlugins()) {
                    Cookie cookie = cookieForName.get(info.code);
                    if (cookie != null) {
                        String val = cookie.getValue();
                        Optional<UserInfo> ui = plugins.decodeCookieValue(val);
                        if (ui.isPresent()) {
                            OAuthPlugin<?> plugin = plugins.getPlugin(info.code);
                            UserInfo uinfo = ui.get();
                            loginAs(evt, uinfo, plugin, uf, info, result);
                        }
                    }
                }
            }
            BasicCredentials creds = null;
            if (settings.getBoolean(SETTINGS_KEY_ENABLE_BASIC_AUTH, true)) {
                creds = evt.getHeader(Headers.AUTHORIZATION);
                if (creds != null) {
                    loginAs(evt, creds, uf, result, hasher);
                }
            }
            if ("true".equals(evt.getParameter("auth")) && result.identities.isEmpty()) {
                add(Headers.WWW_AUTHENTICATE, realm);
                setState(new RespondWith(HttpResponseStatus.UNAUTHORIZED, result));
            } else {
                if (result.identities.isEmpty()) {
                    setState(new RespondWith(code, result));
                } else {
                    // Set a fake auth cookie so things that need the current user name
                    // can decode it from the cookie
                    if (creds != null) {
                        DefaultCookie xck = new DefaultCookie(BasicAuthenticationStrategy.CODE, "--");
                        xck.setDomain(evt.getHeader(Headers.HOST));
                        xck.setMaxAge(plugins.slugMaxAge().getStandardSeconds());
                        xck.setPath(plugins.cookieBasePath());
                        xck.setPorts(plugins.cookiePortList());
                        add(Headers.SET_COOKIE, xck);
                    }
                    setState(new RespondWith(200, result));
                }
            }
        }

        private <T> void loginAs(HttpEvent evt, BasicCredentials creds, UserFactory<T> uf, Result result, PasswordHasher hasher) {
            Optional<T> usero = uf.findUserByName(creds.username);
            if (usero.isPresent()) {
                T user = usero.get();
                Optional<String> pho = uf.getPasswordHash(user);
                if (pho.isPresent()) {
                    String passwordHash = pho.get();
                    String newHashed = hasher.hash(creds.password);
                    if (passwordHash.equals(newHashed)) {
                        String dn = uf.getUserDisplayName(user);
                        String un = uf.getUserName(user);
                        Identity id = new Identity(un, dn, "login", "ba");
                        result.identities.add(id);
                        result.success = true;
                        result.homePage = redir.getRedirectURI(uf, user, evt);
                        if (!plugins.hasDisplayNameCookie(evt)) {
                            plugins.createDisplayNameCookie(evt, response(), dn);
                        }
                    }
                }
            }
        }

        private <T> void loginAs(HttpEvent evt, UserInfo info, OAuthPlugin with, UserFactory<T> uf, PluginInfo pi, Result result) {
            Optional<T> userObject = uf.findUserByName(info.userName);
            if (userObject.isPresent()) {
                T obj = userObject.get();
                Optional<Slug> slugo = uf.getSlug(pi.code, obj, false);
                if (slugo.isPresent()) {
                    Slug slug = slugo.get();
                    Duration maxAge = with.getSlugMaxAge();
                    Duration slugAge = slug.age();
                    if (slugAge.isShorterThan(maxAge)) {
                        String matchWith = plugins.encodeCookieValue(info.userName, slug.slug).split(":")[0];
                        if (info.hashedSlug.equals(matchWith)) {
                            String dn = uf.getUserDisplayName(obj);
                            Identity id = new Identity(uf.getUserName(obj), dn, pi.name, pi.code);
                            result.identities.add(id);
                            result.success = true;
                            result.homePage = redir.getRedirectURI(uf, obj, evt);
                            if (!plugins.hasDisplayNameCookie(evt)) {
                                plugins.createDisplayNameCookie(evt, response(), dn);
                            }
                        }
                    }
                }
            }
        }
    }

    public static class Result {

        public boolean success;
        public List<Identity> identities = new LinkedList<>();
        public String homePage;
    }

    public static final class Identity {

        public String name;
        public String displayName;
        public String serviceName;
        public String serviceCode;

        @JsonCreator //for tests
        public Identity() {
        }

        public Identity(String name, String displayName, String serviceName, String serviceCode) {
            this.name = name;
            this.displayName = displayName;
            this.serviceName = serviceName;
            this.serviceCode = serviceCode;
        }
    }
}
