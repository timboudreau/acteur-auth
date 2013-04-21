package com.timboudreau.trackerapi.support;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.util.BasicCredentials;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.util.PasswordHasher;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Checks;
import com.mastfrog.util.GUIDFactory;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.timboudreau.trackerapi.Properties;
import static com.timboudreau.trackerapi.Properties.name;
import static com.timboudreau.trackerapi.Properties.pass;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.ServerCookieEncoder;
import java.util.List;
import java.util.Set;
import org.bson.types.ObjectId;
import org.joda.time.DateTimeUtils;
import org.joda.time.Duration;

/**
 * Handles basic auth and cookie-based authentication. Basic auth must be
 * accomplished at least once, but this can be done in the background by an ajax
 * call to get the cookie set.
 * <p/>
 * The cookie value consists of: the username as cleartext and delimited by a :
 * character. Following that is a SHA-256 hash of the concatenation of: the
 * username, a system-specific salt, the already-hashed password stored in the
 * database, and a random "slug" value which is stored in the user record.
 * <p/>
 * So, the password is represented as the hash of a hash concatenated with a
 * bunch of other stuff; the slug adds entropy and provides a way to revoke the
 * cookie without expiring it; the system specific salt provides a way to revoke
 * the cookie for all users.
 *
 * @author Tim Boudreau
 */
public final class AuthSupport implements Provider<AuthSupport.Result> {

    static final String COOKIE_NAME = "ac";

    private final Event evt;
    private final DBCollection coll;
    private final PasswordHasher crypto;
    private final Settings settings;
    private final Duration slugMaxAge;
    private final Duration loginCookieMaxAge;

    @Inject
    AuthSupport(DB db, Event evt, PasswordHasher crypto, Settings settings) {
        this.evt = evt;
        this.crypto = crypto;
        this.settings = settings;
        String userCollectionName = settings.getString("users.collection.name", "ttusers");
        coll = db.getCollection(userCollectionName);
        slugMaxAge = new Duration(settings.getLong("cookieSlugMaxAge", Duration.standardHours(3).getMillis()));
        loginCookieMaxAge = new Duration(settings.getLong("loginCookieMaxAge", Duration.standardMinutes(5).getMillis()));
    }

    public String encodeLoginCookie(AuthSupport.Result res) {
        return encodeLoginCookie(res.userObject, res.username, res.hashedPass);
    }

    public String encodeLoginCookie(DBObject user, String username, String hashedPass) {
        Checks.notNull("hashedPass", hashedPass);
        Checks.notNull("user", user);
        Checks.notNull("username", username);

        String slug = getSlug(user, true);
        String rehash = assembleCookieValue(username, hashedPass, slug);

        String host = getHost();

        DefaultCookie lcookie = new DefaultCookie(COOKIE_NAME, rehash);
        lcookie.setDomain(host);
        lcookie.setMaxAge(loginCookieMaxAge.getMillis());
        lcookie.setPorts(80, 7739);
        lcookie.setPath("/");
        lcookie.setHttpOnly(true);

        String encoded = ServerCookieEncoder.encode(lcookie);
        System.out.println("COOKIE: " + encoded);
        return encoded;
    }

    public Result getAuthResult() {
        BasicCredentials credentials = evt.getHeader(Headers.AUTHORIZATION);
        if (credentials == null) {
            return new Result(ResultType.NO_CREDENTIALS, false);
        }
        if (credentials.username == null || credentials.username.length() < 3) {
            return new Result(ResultType.INVALID_CREDENTIALS, false);
        }
        DBObject u = coll.findOne(new BasicDBObject(name, credentials.username));
        if (u == null) {
            return new Result(ResultType.NO_RECORD, credentials.username, false);
        }
        String hashedPassword = (String) u.get(pass);
        if (hashedPassword == null) {
            return new Result(ResultType.BAD_RECORD, credentials.username, false);
        }
        if (!crypto.checkPassword(credentials.password, hashedPassword)) {
            return new Result(ResultType.BAD_PASSWORD, credentials.username, false);
        }
        List<ObjectId> authorizes = (List<ObjectId>) u.get("authorizes");
        Number userVersion = (Number) u.get(Properties.version);
        String displayName = (String) u.get(Properties.displayName);
        int version = userVersion == null ? 0 : userVersion.intValue();
        TTUser user = new TTUser(credentials.username, (ObjectId) u.get("_id"), version, displayName, authorizes);
        return new Result(user, u, credentials.username, hashedPassword, ResultType.SUCCESS, false);
    }

    private String assembleCookieValue(String username, String hashedPass, String slug) {
        String salt = settings.getString("cookieSalt");
        return username + ':' + crypto.encryptPassword(new StringBuilder()
                .append(username)
                .append(salt)
                .append(hashedPass)
                .append(slug).toString());
    }

    private String getHost() {
        String host = evt.getHeader(HttpHeaders.Names.HOST);
        if (host != null) {
            if (host.indexOf(':') > 0) {
                host = host.substring(0, host.indexOf(':'));
            }
        } else {
            host = "unknown.example"; //going to fail anyway
        }
        return host;
    }

    public String clearCookie() {
        DefaultCookie ck = new DefaultCookie(COOKIE_NAME, "x");
        ck.setDomain(getHost());
        ck.setMaxAge(0);
        ck.setDiscard(true);
        ck.setPath("/");
        System.out.println("CLEAR COOKIE");
        return ServerCookieEncoder.encode(ck);
    }

    private String newSlug() {
        return GUIDFactory.get().newGUID(2, 9) + System.currentTimeMillis();
    }

    private String getSlug(DBObject user, boolean create) {
        String slug = null;
        DBObject result = (DBObject) user.get("cookieSlug");
        long now = DateTimeUtils.currentTimeMillis();

        System.out.println("Get slug " + user.get("name") + " create " + create);

        if (result != null) {
            Number n = (Number) result.get("created");
            if (n == null) {
                System.out.println("No slug date");
                result = null;
            } else {
                long then = n.longValue();
                if (now < then) { // created in the future?
                    System.out.println("slug created in the future");
                    result = null;
                } else if (new Duration(now - then).isLongerThan(slugMaxAge)) {
                    System.out.println("Slug too old");
                    result = null;
                } else {
                    slug = (String) result.get("value");
                    System.out.println("Found slug, value is " + slug);
                }
            }
        } else {
            System.out.println("No slug object");
        }
        if (slug == null && create) {
            System.out.println("Create a new slug");
            slug = newSlug();
            // In theory this will fail if the user is concurrently updated
            // via another connection, but that's rare and likely a bad sign
            BasicDBObject query = new BasicDBObject("_id", user.get("_id")).append("version", user.get("version"));
            BasicDBObject toSet = new BasicDBObject("value", slug).append("created", now);
            BasicDBObject cookieSlugObject = new BasicDBObject("cookieSlug", toSet);
            BasicDBObject edit = new BasicDBObject("$set", cookieSlugObject);
            BasicDBObject inc = new BasicDBObject("version", 1);
            edit.append("$inc", inc);
            System.out.println("WRITE SLUG - EDIT: " + edit.toMap());
            DBObject updated = coll.findAndModify(query, edit);
            System.out.println("Updated user: " + updated.toMap());
        }
        return slug;
    }

    public Result get() {
        Result result = getAuthResult();
        // If login failed, try the cookie
        // But not if login credentials were actually provided
        if (!result.type.isSuccess() && result.username == null) {
            Result b = getCookieResult();
            if (b.isSuccess()) {
                result = b;
            } else {
                result = Result.combined(result, b);
            }
        }
        return result;
    }

    public String findCookie() {
        return findCookie(COOKIE_NAME);
    }

    public String findCookie(String name) {
        String cookie = evt.getHeader(HttpHeaders.Names.COOKIE);
        if (cookie != null) {
            Set<Cookie> cookies = CookieDecoder.decode(cookie);
            for (Cookie ck : cookies) {
                if (name.equals(ck.getName())) {
                    return ck.getValue();
                }
            }
        }
        return null;
    }
    
    public Cookie encodeDisplayNameCookie(String displayName) {
        DefaultCookie ck = new DefaultCookie("dn", displayName);
        ck.setDomain(getHost());
        ck.setMaxAge(Duration.standardDays(365).getMillis());
        ck.setPorts(80, 7739);//XXX get port from settings?
        ck.setPath("/");
        ck.setHttpOnly(false);
        return ck;
    }

    public Result getCookieResult() {

        String loginCookie = findCookie();
        if (loginCookie == null) {
            return new Result(ResultType.NO_CREDENTIALS, true);
        }
        System.out.println("Found login cookie");
        String[] parts = loginCookie.split(":", 2);
        if (parts.length == 2) {
            if (parts[0].length() < 3) {
                return new Result(ResultType.INVALID_CREDENTIALS, true);
            }
            System.out.println("USER NAME: " + parts[0]);
            DBObject u = coll.findOne(new BasicDBObject(name, new String[]{parts[0]}));
            if (u != null) {
                String slug = getSlug(u, false);
                System.out.println("GET SLUG FOR " + parts[0] + " - " + u.get("cookieSlug") + " gets " + slug);
                if (slug == null) {
                    return new Result(ResultType.NO_CREDENTIALS, true);
                }
                String pass = (String) u.get("pass");
                if (pass != null) {
                    List<String> names = (List<String>) u.get("name");
                    for (String name : names) {
                        String check = assembleCookieValue(name, pass, slug);
                        if (check.equals(loginCookie)) {
                            List<ObjectId> authorizes = (List<ObjectId>) u.get("authorizes");
                            Number ver = (Number) u.get(Properties.version);
                            int version = ver == null ? 0 : ver.intValue();
                            String displayName = (String) u.get(Properties.displayName);
                            TTUser user = new TTUser(parts[0], (ObjectId) u.get(Properties._id), version, displayName, authorizes);
                            return new Result(user, u, parts[0], pass, ResultType.SUCCESS, true);
                        }
                    }
                    return new Result(ResultType.INVALID_CREDENTIALS, parts[0], true);
                } else {
                    return new Result(ResultType.BAD_RECORD, parts[0], true);
                }
            } else {
                System.out.println("No db object, bail");
                return new Result(ResultType.NO_RECORD, parts[0], true);
            }
        } else {
            System.out.println("Weird credentials");
            return new Result(ResultType.INVALID_CREDENTIALS, true);
        }
    }

    public static class Result {

        public final TTUser user;
        public final DBObject userObject;
        public final String username;
        public final String hashedPass;
        public final ResultType type;
        public final boolean cookie;

        public Result(ResultType type, String username, boolean cookie) {
            this(null, null, username, null, type, cookie);
        }

        public Result(ResultType type, boolean cookie) {
            this(null, null, null, null, type, cookie);
        }

        public Result(TTUser user, DBObject userObject, String username, String hashedPass, ResultType type, boolean cookie) {
            this.user = user;
            this.userObject = userObject;
            this.username = username;
            this.hashedPass = hashedPass;
            this.type = type;
            this.cookie = cookie;
            System.out.println("AUTH RESULT: " + this);
        }

        static Result combined(Result a, Result b) {
            // We want cookie to be true if a cookie was present
            boolean ck = a.isSuccess() && a.cookie;
            if (!ck) {
                ck = b.isSuccess() && b.cookie;
            }
            return new Result(a.user == null ? b.user : null, a.userObject == null ? b.userObject : null,
                    a.username == null ? b.username : null, a.hashedPass == null ? b.hashedPass : a.hashedPass,
                    a.type, ck);
        }

        public boolean isSuccess() {
            return type.isSuccess();
        }

        @Override
        public String toString() {
            return "Result{" + "user=" + user + ", username=" + username
                    + ", hashedPass=" + hashedPass + ", type=" + type + ", cookie="
                    + cookie + '}';
        }
    }

    public enum ResultType {

        NO_CREDENTIALS,
        NO_RECORD,
        INVALID_CREDENTIALS,
        BAD_CREDENTIALS,
        BAD_RECORD,
        BAD_PASSWORD,
        SUCCESS;

        public boolean isSuccess() {
            return this == SUCCESS;
        }
    }
}