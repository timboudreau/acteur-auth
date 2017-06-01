package com.mastfrog.acteur.auth;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.acteur.auth.MockUserFactory.MockUser;
import com.mastfrog.util.Checks;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author tim
 */
@Singleton
public class MockUserFactory extends UserFactory<MockUser> {

    private final Map<String, MockUser> all = new HashMap<>();

    @Inject
    public MockUserFactory(UniqueIDs ids) {
        super(MockUser.class, ids);
    }

    @Override
    public Optional<MockUser> findUserByName(String name) {
        if ("nobody".equals(name)) {
            return Optional.absent();
        }
        MockUser mu = all.get(name);
        if (mu == null) {
            mu = new MockUser(name);
            all.put(name, mu);
        }
        return Optional.of(mu);
    }

    @Override
    public Optional<String> getPasswordHash(MockUser user) {
        return user.optional("pass");
    }

    @Override
    public void setPasswordHash(MockUser on, String hash) {
        System.out.println("Set pw hach " + on + " to " + hash);
        on.put("pass", hash);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Slug getSlug(MockUser on, String name) {
        Map<String, Object> slugs = (Map<String, Object>) on.get("slugs");
        if (slugs == null) {
            slugs = new HashMap<>();
            on.put("slugs", slugs);
        }
        return (Slug) slugs.get(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void putSlug(MockUser on, Slug slug) {
        if (slug.name.equals("fake")) {
            throw new Error();
        }
        Map<String, Object> slugs = (Map<String, Object>) on.get("slugs");
        if (slugs == null) {
            slugs = new HashMap<>();
            on.put("slugs", slugs);
        }
        slugs.put(slug.name, slug);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getSlugNames(MockUser on) {
        Map<String, Object> slugs = (Map<String, Object>) on.get("slugs");
        return slugs == null ? Collections.<String>emptySet() : slugs.keySet();
    }

    @Override
    public MockUser newUser(String name, Slug slug, String displayName, Map<String, Object> properties, OAuthPlugin plugin) {
        System.out.println("CREATE  " + name);
        if (all.containsKey(name)) {
            throw new IllegalStateException(name);
        }
        MockUser mu = new MockUser(name);
        Map<String, Object> slugs = new HashMap<>();
        mu.put("displayName", displayName);
        mu.put("slugs", slugs);
        if (slug != null) {
            if (slug.name.equals("fake")) {
                throw new Error();
            }
            slugs.put(slug.name, slug);
        }
        if (plugin != null && properties != null) {
            putData(mu, plugin.name, properties);
        }
        all.put(name, mu);
        return mu;
    }

    @Override
    public MockUser newUser(String name, String hashedPassword, String displayName, Map<String, Object> properties) {
        MockUser result = newUser(name, (Slug) null, displayName, properties, null);
        setPasswordHash(result, hashedPassword);
        return result;
    }

    @Override
    public Object toUserObject(MockUser obj) {
        return obj;
    }

    private final Set<LoginState> states = new HashSet<>();

    @Override
    protected void saveLoginState(LoginState state) {
        states.add(state);
    }

    public Set<LoginState> states() {
        return states;
    }

    @Override
    public Optional<LoginState> lookupLoginState(String state) {
        for (LoginState s : states) {
            if (state.equals(s.state)) {
                return Optional.of(s);
            }
        }
        return Optional.absent();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void putAccessToken(MockUser on, String token, String serviceName) {
        Map<String, Object> tokens = (Map<String, Object>) on.get("tokens");
        if (tokens == null) {
            tokens = new HashMap<>();
            on.put("tokens", tokens);
        }
        tokens.put(serviceName, token);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<String> getAccessToken(MockUser on, String serviceName) {
        Map<String, Object> tokens = (Map<String, Object>) on.get("tokens");
        if (tokens == null) {
            return Optional.absent();
        }
        return Optional.fromNullable((String) tokens.get("serviceName"));
    }

    @Override
    public String getUserDisplayName(MockUser obj) {
        Checks.notNull("obj", obj);
        return (String) (obj.get("displayName") == null ? obj.get("name") : obj.get("displayName"));
    }

    @Override
    public String getUserName(MockUser obj) {
        return (String) obj.get("name");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void putData(MockUser user, String name, Map<String, Object> data) {
        Checks.notNull("data", data);
        Checks.notNull("user", user);
        Checks.notNull("name", name);
        Map<String, Object> dta = (Map<String, Object>) user.get("data");
        if (dta == null) {
            dta = new HashMap<>();
        }
        dta.put(name, data);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getData(MockUser user, String name) {
        Checks.notNull("name", name);
        Checks.notNull("user", user);
        Map<String, Object> dta = (Map<String, Object>) user.get("data");
        if (dta == null) {
            return new HashMap<>();
        }
        Map<String, Object> result = (Map<String, Object>) dta.get(name);
        if (result == null) {
            return new HashMap<>();
        }
        return result;
    }

    @Override
    public Optional<MockUser> findUserBy(String key, String value) {
        for (MockUser m : this.all.values()) {
            if (value.equals(m.get(key))) {
                return Optional.<MockUser>of(m);
            }
        }
        return Optional.absent();
    }

    static class MockUser extends HashMap<String, Object> {

        MockUser(String name) {
            this.put("name", name);
            this.put("version", 0);
            this.put("created", ZonedDateTime.now());
        }

        public String getString(String key) {
            Object o = get(key);
            return o == null ? null : o + "";
        }

        public Optional<String> optional(String key) {
            return Optional.fromNullable(getString(key));
        }
    }
}
