package com.mastfrog.acteur;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.acteur.MockUserFactory.MockUser;
import com.mastfrog.acteur.auth.UniqueIDs;
import com.mastfrog.acteur.auth.UserFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.joda.time.DateTime;

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
        return Optional.fromNullable(user.getString("pass"));
    }

    @Override
    public void setPasswordHash(MockUser on, String hash) {
        on.put("pass", hash);
    }

    @Override
    protected Slug getSlug(MockUser on, String name) {
        Map<String, Object> slugs = (Map<String, Object>) on.get("slugs");
        if (slugs == null) {
            slugs = new HashMap<>();
            on.put("slugs", slugs);
        }
        return (Slug) slugs.get(name);
    }

    @Override
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
    public Set<String> getSlugNames(MockUser on) {
        Map<String, Object> slugs = (Map<String, Object>) on.get("slugs");
        return slugs == null ? Collections.<String>emptySet() : slugs.keySet();
    }

    @Override
    public MockUser newUser(String name, Slug slug, String displayName, Map<String, Object> properties) {
        System.out.println("CREATE  " + name);
        if (all.containsKey(name)) {
            throw new IllegalStateException(name);
        }
        MockUser mu = new MockUser(name);
        Map<String, Object> slugs = new HashMap<>();
        mu.put("slugs", slugs);
        if (slug.name.equals("fake")) {
            throw new Error();
        }
        if (slug != null) {
            slugs.put(slug.name, slug);
        }
        mu.putAll(properties);
        all.put(name, mu);
        return mu;
    }

    @Override
    public MockUser newUser(String name, String hashedPassword, String displayName, Map<String, Object> properties) {
        MockUser result = newUser(name, (Slug) null, displayName, properties);
        result.put("pass", hashedPassword);
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
    public void putAccessToken(MockUser on, String token, String serviceName) {
        Map<String, Object> tokens = (Map<String, Object>) on.get("tokens");
        if (tokens == null) {
            tokens = new HashMap<>();
            on.put("tokens", tokens);
        }
        tokens.put(serviceName, token);
    }

    @Override
    public Optional<String> getAccessToken(MockUser on, String serviceName) {
        Map<String, Object> tokens = (Map<String, Object>) on.get("tokens");
        if (tokens == null) {
            return Optional.absent();
        }
        return Optional.fromNullable((String) tokens.get("serviceName"));
    }

    static class MockUser extends HashMap<String, Object> {

        MockUser(String name) {
            this.put("name", name);
            this.put("version", 0);
            this.put("created", new DateTime());
        }

        public String getString(String key) {
            Object o = get(key);
            return o == null ? null : o + "";
        }

        public Optional<String> optional(String key) {
            return Optional.fromNullable(getString(key));
        }
        
        public String toString() {
            return (String) get("name");
        }
    }
}
