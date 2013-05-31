package com.mastfrog.acteur.auth;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.Response;
import com.mastfrog.acteur.auth.TestLoginPage.TestLoginActeur;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.util.Headers;
import static com.mastfrog.acteur.util.Method.GET;
import com.mastfrog.acteur.util.PasswordHasher;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.Host;
import com.mastfrog.url.Path;
import com.mastfrog.util.Checks;
import com.mastfrog.util.ConfigurationError;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultCookie;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * Registry of OAuth plugins
 *
 * @author Tim Boudreau
 */
@Singleton
public final class OAuthPlugins implements Iterable<OAuthPlugin<?>> {

    private final List<OAuthPlugin<?>> all = new CopyOnWriteArrayList<>();
    public static final String OAUTH_LANDING_PAGE_BASE_SETTINGS_KEY = "oauth.plugin.landing.page.base";
    public static final String OAUTH_BOUNCE_PAGE_BASE_SETTINGS_KEY = "oauth.plugin.bounce.page.base";
    private final Settings settings;
    private final PathFactory pf;
    public static final String SETTINGS_KEY_COOKIE_SALT = "oauth.cookie.salt";
    private static final String DEFAULT_COOKIE_SALT = "asd#(#(f889asud(%&#_djAOKcausd89cj2k24hSj0000ss03w:@#*(#@#(";
    private final String salt;
    private final PasswordHasher hasher;
    public static final String SETTINGS_KEY_LOGIN_REDIRECT = "oauth.login.redirect";
    public static final String SETTINGS_KEY_SLUG_MAX_AGE_HOURS = "oauth.slug.max.age.hours";
    private final URI loginRedirectURI;
    private final Duration slugMaxAge;
    public static final String SETTINGS_KEY_OAUTH_COOKIE_PATH = "oauth.cookie.path";
    public static final String SETTINGS_KEY_OAUTH_COOKIE_HOST = "oauth.cookie.host";
    private final int[] ports;
    private final String cookieBasePath;
    private final String cookieHost;
    public static final String DISPLAY_NAME_COOKIE_NAME = "dn";
    public static final String SETTINGS_KEY_DISPLAY_NAME_COOKIE_MAX_AGE_DAYS = "display.name.cookie.max.age.days";
    private final boolean useDisplayNameCookie;
    private final Duration displayNameCookieMaxAge;
    public static final String SETTINGS_KEY_USE_DISPLAY_NAME_COOKIE = "use.display.name.cookie";

    @Inject
    OAuthPlugins(Settings settings, PathFactory pf, Dependencies deps, PasswordHasher hasher) throws URISyntaxException {
        this.settings = settings;
        this.pf = pf;
        this.hasher = hasher;
        long displayNameCookieMaxAge = settings.getLong(SETTINGS_KEY_DISPLAY_NAME_COOKIE_MAX_AGE_DAYS, 60);
        useDisplayNameCookie = settings.getBoolean(SETTINGS_KEY_USE_DISPLAY_NAME_COOKIE, true);
        this.displayNameCookieMaxAge = Duration.standardDays(displayNameCookieMaxAge);
        salt = settings.getString(SETTINGS_KEY_COOKIE_SALT, DEFAULT_COOKIE_SALT);
        if (deps.isProductionMode() && salt == DEFAULT_COOKIE_SALT) {
            throw new ConfigurationError("Will not run in production mode "
                    + "with the default cookie salt which makes auth cookies "
                    + "predictable.  Set '" + SETTINGS_KEY_COOKIE_SALT + "' "
                    + "in your settings.");
        }
        this.loginRedirectURI = new URI(settings.getString(SETTINGS_KEY_LOGIN_REDIRECT, "/"));
        this.slugMaxAge = Duration.standardHours(settings.getInt(SETTINGS_KEY_SLUG_MAX_AGE_HOURS, 3));
        Integer runningPort = settings.getInt("port");
        // XXX may want to be able to explicitly set all the ports
        if (runningPort != null) {
            ports = new int[]{80, 443, runningPort};
        } else {
            ports = new int[]{80, 443};
        }
        // The path property for cookies
        cookieBasePath = settings.getString(SETTINGS_KEY_OAUTH_COOKIE_PATH, "/");
        cookieHost = settings.getString(SETTINGS_KEY_OAUTH_COOKIE_HOST);
    }

    List<Integer> cookiePortList() {
        List<Integer> l = new ArrayList(cookiePorts().length);
        for (int i : cookiePorts()) {
            l.add(i);
        }
        return l;
    }

    int[] cookiePorts() {
        return ports;
    }

    String cookieBasePath() {
        return cookieBasePath;
    }

    public Class<? extends Acteur> testLoginActeurType() {
        return TestLoginActeur.class;
    }

    public Class<? extends Page> testLoginPageType() {
        return TestLoginPage.class;
    }

    public Class<? extends Acteur> landingActeurType() {
        return OAuthLandingPageActeur.class;
    }

    public Class<? extends Acteur> bounceActeurType() {
        return InitiateOAuthActeur.class;
    }

    public Class<? extends Page> bouncePageType() {
        return BouncePage.class;
    }

    public Class<? extends Page> landingPageType() {
        return LandingPage.class;
    }

    public Class<? extends Page> listOAuthProvidersPageType() {
        return ListAuthsPage.class;
    }

    public Duration slugMaxAge() {
        return slugMaxAge;
    }

    public URI loginRedirect() {
        return loginRedirectURI;
    }

    public String getBouncePageBasePath() {
        String base = settings.getString(OAUTH_BOUNCE_PAGE_BASE_SETTINGS_KEY, "oauth");
        return base;
    }

    public String getLandingPageBasePath() {
        String base = settings.getString(OAUTH_LANDING_PAGE_BASE_SETTINGS_KEY, "login");
        return base;
    }

    Set<String> cookieNames() {
        Set<String> result = new HashSet<>();
        for (OAuthPlugin<?> p : this) {
            result.add(p.code());
        }
        return result;
    }

    private final Host getHost(Event evt) {
        Host host = evt.getHeader(Headers.HOST);
        if (cookieHost != null) {
            host = Host.parse(cookieHost);
        } else if (host == null) {
            host = Host.parse("fail.example");
        }
        return host;
    }

    public void createDisplayNameCookie(Event evt, Response response, String displayName) {
        if (useDisplayNameCookie) {
            DefaultCookie displayNameCookie = new DefaultCookie(DISPLAY_NAME_COOKIE_NAME, displayName);
            displayNameCookie.setDomain(getHost(evt).toString()); //XXX use a setting?
            displayNameCookie.setDiscard(true);
            displayNameCookie.setPorts(cookiePortList());
            displayNameCookie.setPath(cookieBasePath());
            displayNameCookie.setMaxAge(displayNameCookieMaxAge.getStandardSeconds());
            response.add(Headers.SET_COOKIE, displayNameCookie);
        }
    }

    public boolean hasDisplayNameCookie(Event evt) {
        Cookie[] cookies = evt.getHeader(Headers.COOKIE);
        if (cookies == null) {
            return false;
        }
        for (Cookie ck : cookies) {
            if (DISPLAY_NAME_COOKIE_NAME.equals(ck.getName())) {
                return true;
            }
        }
        return false;
    }

    public void logout(Event evt, Response response) {
        Checks.notNull("response", response);
        Checks.notNull("evt", evt);
        Cookie[] cks = evt.getHeader(Headers.COOKIE);
        if (cks != null) {
            Host host = getHost(evt);
            if (host == null) {
                return;
            }
            Set<String> all = cookieNames();
            all.add(BasicAuthenticationStrategy.CODE);
            all.add(OAuthPlugins.DISPLAY_NAME_COOKIE_NAME);
            for (Cookie ck : cks) {
                if (all.contains(ck.getName())) {
                    DefaultCookie discardCookie = new DefaultCookie(ck.getName(), "-");
                    discardCookie.setDomain(host.toString()); //XXX use a setting?
                    discardCookie.setDiscard(true);
                    discardCookie.setPorts(cookiePortList());
                    discardCookie.setPath(cookieBasePath());
                    discardCookie.setMaxAge(0);
                    response.add(Headers.SET_COOKIE, discardCookie);
                }
            }
        }
    }

    public List<PluginInfo> getPlugins() {
        List<PluginInfo> result = new ArrayList<>(all.size());
        String base = settings.getString(OAUTH_BOUNCE_PAGE_BASE_SETTINGS_KEY, "oauth");
        String landingBase = settings.getString(OAUTH_LANDING_PAGE_BASE_SETTINGS_KEY, "login");
        Path pth = Path.parse(base);
        for (OAuthPlugin<?> p : all) {
            Path path = pf.toExternalPath(pth.append(p.code()));
            Path landingPath = pf.toExternalPath(Path.parse(landingBase).append(p.code()));
            result.add(new PluginInfo(p.code(), p.name(), path.toStringWithLeadingSlash(), 
                    p.getLogoUrl(), landingPath.toStringWithLeadingSlash()));
        }
        return result;
    }

    public Optional<OAuthPlugin<?>> find(String code) {
        Checks.notNull("code", code);
        for (OAuthPlugin<?> p : all) {
            if (code.equals(p.code())) {
                return Optional.<OAuthPlugin<?>>of(p);
            }
        }
        return Optional.absent();
    }

    void register(OAuthPlugin<?> plugin) {
        Checks.notNull("plugin", plugin);
        Optional<OAuthPlugin<?>> existing = find(plugin.code());
        if (existing.isPresent()) {
            throw new ConfigurationError(plugin + " registered twice "
                    + "- perhaps it is not bound as a singleton?");
        }
        all.add(plugin);
    }

    public String encodeCookieValue(String username, String slug) {
        Checks.notNull("slug", slug);
        Checks.notNull("username", username);
        String hashed = hasher.encryptPassword(username + slug + salt);
        return hashed + ":" + username;
    }

    public Optional<UserInfo> decodeCookieValue(String cookievalue) {
        Checks.notNull("cookievalue", cookievalue);
        int ix = cookievalue.indexOf(':');
        if (ix <= 0) {
            return Optional.absent();
        }
        String hashedSlug = cookievalue.substring(0, ix);
        String username = cookievalue.substring(ix + 1);
        return Optional.of(new UserInfo(username, hashedSlug));
    }

    @Override
    public Iterator<OAuthPlugin<?>> iterator() {
        return Collections.unmodifiableCollection(all).iterator();
    }

    public OAuthPlugin getPlugin(String code) {
        for (OAuthPlugin plugin : this) {
            if (plugin.code().equals(code)) {
                return plugin;
            }
        }
        return null;
    }

    public static class PluginInfo {

        public final String code;
        public final String name;
        public final String loginPagePath;
        public final String logoUrl;
        public final String landingPagePath;

        public PluginInfo(String code, String name, String path, String logoUrl, String landingPagePath) {
            this.code = code;
            this.name = name;
            this.loginPagePath = path;
            this.logoUrl = logoUrl;
            this.landingPagePath = landingPagePath;
        }

        public boolean equals(Object o) {
            return o instanceof PluginInfo && ((PluginInfo) o).code.equals(code);
        }

        public int hashCode() {
            return code.hashCode();
        }

        public String toString() {
            return name + ':' + code + ':' + loginPagePath + ':' + landingPagePath;
        }
    }

    static class BouncePage extends Page {

        @Inject
        BouncePage(ActeurFactory af, OAuthPlugins plgns) {
            add(af.matchMethods(GET));
            add(af.matchPath(plgns.getBouncePageBasePath() + "/.*"));
            add(InitiateOAuthActeur.class);
        }
        
        @Override
        protected String getDescription() {
            return "Redirects to an oauth provider whose code is the "
                    + "second path component";
        }
    }

    static class LandingPage extends Page {
        private final OAuthPlugins plgns;

        @Inject
        LandingPage(ActeurFactory af, OAuthPlugins plgns) {
            this.plgns = plgns;
            add(af.matchMethods(GET));
            add(af.matchPath(plgns.getLandingPageBasePath() + "/.*"));
            add(OAuthLandingPageActeur.class);
        }
        
        @Override
        protected String getDescription() {
            StringBuilder sb = new StringBuilder();
            for (PluginInfo info : plgns.getPlugins()) {
                if (sb.length() != 0) {
                    sb.append(", ");
                }
                sb.append(plgns.getLandingPageBasePath()).append("/").append(info.code).append( " -> ").append(info.name);
            }
            return "OAuth callback page - the exact service is determined "
                    + "by the last path element of the URL as follows: " + sb;
        }
        
    }

    public static final String SETTINGS_KEY_OAUTH_TYPES_PAGE_PATH = "oauth.types.page.path";

    static class ListAuthsPage extends Page {

        @Inject
        ListAuthsPage(Settings settings, DateTime systemStartTime, ActeurFactory af) {
            getReponseHeaders().setLastModified(systemStartTime);
            getReponseHeaders().addCacheControl(CacheControlTypes.Public);
            getReponseHeaders().addCacheControl(CacheControlTypes.must_revalidate);
            getReponseHeaders().addCacheControl(CacheControlTypes.max_age, Duration.standardHours(2));
            getReponseHeaders().setExpires(DateTime.now().plus(Duration.standardHours(2)));
            String pth = "^" + settings.getString(SETTINGS_KEY_OAUTH_TYPES_PAGE_PATH, "authtypes") + "$";
            add(af.matchMethods(GET));
            add(af.matchPath(pth));
            add(af.sendNotModifiedIfIfModifiedSinceHeaderMatches());
            add(ListAuthsActeur.class);
        }

        @Override
        protected String getDescription() {
            return "List OAuth authentication methods supported";
        }

        static class ListAuthsActeur extends Acteur {

            @Inject
            ListAuthsActeur(OAuthPlugins plugins) {
                setState(new RespondWith(OK, plugins.getPlugins()));
            }
        }
    }
}
