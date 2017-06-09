package com.mastfrog.acteur.auth;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.acteur.HttpEvent;
import static com.mastfrog.acteur.auth.OAuthPlugins.SETTINGS_KEY_OAUTH_COOKIE_HOST;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.settings.Settings;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public final class VisitorCookies {

    private final String cookieName;
    private final Duration cookieDuration;
    public static final String SETTINGS_KEY_COOKIE_NAME = "visitor.cookie.name";
    public static final String SETTINGS_KEY_COOKIE_DURATION_DAYS = "visitor.cookie.duration.days";
    public static final String DEFAULT_COOKIE_NAME = "bid";
    private final String cookieHost;
    private final UniqueIDs ids;
    private final int port;

    @Inject
    VisitorCookies(Settings settings, UniqueIDs ids) {
        cookieName = settings.getString(SETTINGS_KEY_COOKIE_NAME, DEFAULT_COOKIE_NAME);
        cookieDuration = Duration.ofDays(settings.getInt(SETTINGS_KEY_COOKIE_DURATION_DAYS, 365 * 5));
        cookieHost = settings.getString(SETTINGS_KEY_OAUTH_COOKIE_HOST);
        port = settings.getInt("port", 8_133); //XXX
        this.ids = ids;
    }

    public Optional<String> visitorId(HttpEvent evt) {
        Cookie[] ck = evt.header(Headers.COOKIE_B);
        if (ck == null) {
            return Optional.absent();
        }
        for (Cookie c : ck) {
            if (cookieName.equals(c.name())) {
                return Optional.of(c.value());
            }
        }
        return Optional.absent();
    }

    public <T> Cookie associateCookieWithUser(HttpEvent evt, UserFactory<T> users, T user) {
        Optional<String> ido = visitorId(evt);
        if (!ido.isPresent()) {
            String newId = Long.toString(System.currentTimeMillis(), 36) + '-' + ids.newRandomString(4);
            return createCookie(newId, evt, users, user);
        } else if (users != null) {
            saveCookieInfo(evt, users, user, ido.get());
        }
        return null;
    }

    public <T> Cookie createCookieIfAbsent(HttpEvent evt, UserFactory<T> users, T user) {
        Optional<String> ido = visitorId(evt);
        if (!ido.isPresent()) {
            String newId = Long.toString(System.currentTimeMillis(), 36) + '-' + ids.newRandomString(4);
            return createCookie(newId, evt, users, user);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> void saveCookieInfo(HttpEvent evt, UserFactory<T> users, T user, String newId) {
        Map<String, Object> data = new HashMap<>(users.getData(user, cookieName));
        String userAgent = evt.header("User-Agent");
        if (userAgent == null) {
            userAgent = "Unknown";
        }
        List<String> forUa = (List<String>) data.get(userAgent);
        if (forUa == null) {
            forUa = new LinkedList<>();
            data.put(userAgent, forUa);
        }
        if (!forUa.contains(newId)) {
            forUa.add(newId);
            users.putData(user, cookieName, data);
        }
    }

    private <T> Cookie createCookie(String newId, HttpEvent evt, UserFactory<T> users, T user) {
        DefaultCookie ck = new DefaultCookie(cookieName, newId);
        ck.setMaxAge(cookieDuration.getSeconds());
        ck.setPath("/");
//        ck.setPorts(80, 443, port);
        String host = cookieHost == null ? evt.header("Host") : cookieHost;
        ck.setDomain(host);
        if (users != null) {
            saveCookieInfo(evt, users, user, newId);
        }
        return ck;
    }
}
