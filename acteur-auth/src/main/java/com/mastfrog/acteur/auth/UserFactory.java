package com.mastfrog.acteur.auth;

import com.google.common.base.Optional;
import java.util.Map;
import java.util.Set;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * Implements the features of a "user" object necessary for authentication
 * without tying the authentication implementation to any particular user object
 * type.
 *
 * @author Tim Boudreau
 */
public abstract class UserFactory<T> {

    protected final Class<T> type;
    protected final UniqueIDs ids;

    protected UserFactory(Class<T> type, UniqueIDs ids) {
        this.type = type;
        this.ids = ids;
    }

    public final Class<T> type() {
        return type;
    }

    /**
     * Look up a user object by name
     *
     * @param name The name
     * @return A user or null
     */
    public abstract Optional<T> findUserByName(String name);

    /**
     * Get the hashed stored password for a user
     *
     * @param user The user, as returned by findUserByName or newUser
     * @return A hash of a password or null
     */
    public abstract Optional<String> getPasswordHash(T user);

    /**
     * Set the user's password hash, possibly overwriting it
     *
     * @param on
     * @param hash
     */
    public abstract void setPasswordHash(T on, String hash);

    /**
     * Get a slug - a unique random string which maps to a set of login
     * credentials for a given oauth site for a given user. The slug should not
     * be sent over the wire in the clear since it is a password-equivalent;
     * rather it should be hashed with some other data known only to the server,
     * and the result of this hashing compared with incoming data to
     * authenticate.
     *
     * @param name The name of the slug
     * @param on A user object
     * @return A slug
     */
    public final Optional<Slug> getSlug(String name, T on, boolean createIfMissingOrExpired) {
        Slug slug = getSlug(on, name);
        if (slug == null && createIfMissingOrExpired) {
            slug = newSlug(name);
            putSlug(on, slug);
        }
        return Optional.fromNullable(slug);
    }

    public final Slug newSlug(String name) {
        String nue = ids.newId();
        return new Slug(name, nue, DateTime.now());
    }

    protected abstract void putSlug(T on, Slug slug);

    protected abstract Slug getSlug(T on, String name);

    public abstract Optional<String> getAccessToken(T on, String serviceName);

    public abstract void putAccessToken(T on, String token, String serviceName);

    public abstract Set<String> getSlugNames(T on);

    public abstract T newUser(String name, Slug slug, String displayName, Map<String, Object> properties);

    public abstract T newUser(String name, String hashedPassword, String displayName, Map<String, Object> properties);

    public abstract Object toUserObject(T obj);

    public abstract String getUserDisplayName(T obj);
    /**
     * Create and store a new random string which can be passed to an oauth
     * callback
     *
     * @return
     */
    final LoginState newLoginState(String redirectTo) {
        LoginState state = new LoginState(ids.newId(), redirectTo);
        saveLoginState(state);
        return state;
    }

    protected abstract void saveLoginState(LoginState state);

    /**
     * Determine if the passed string was recently created by a call to
     * <code>newLoginState()</code>
     *
     * @param state The login state
     * @return true if it is a known string
     */
    public abstract Optional<LoginState> lookupLoginState(String state);

    public static class LoginState {

        public final String state;
        public final DateTime created;
        public final String redirectTo;

        public LoginState(String state, String redirectTo, DateTime created) {
            this.state = state;
            this.redirectTo = redirectTo;
            this.created = created;
        }

        public LoginState(String state, String redirectTo) {
            this.state = state;
            this.redirectTo = redirectTo;
            created = new DateTime();
        }

        public boolean equals(Object o) {
            return o instanceof LoginState && ((LoginState) o).state.equals(state);
        }

        public int hashCode() {
            return state.hashCode();
        }

        public String toString() {
            return state;
        }
    }

    public static class Slug {

        public final String name;
        public final String slug;
        public final DateTime created;

        public Slug(String name, String slug, DateTime created) {
            this.name = name;
            this.slug = slug;
            this.created = created;
        }

        public Duration age() {
            return new Duration(created, DateTime.now());
        }
        
        public String toString() {
            return name + '=' + slug + " (" + created + ")";
        }
    }
}
