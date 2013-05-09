package com.mastfrog.acteur;

import com.google.common.base.Optional;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.mastfrog.acteur.OAuthPluginsTest.M;
import com.mastfrog.acteur.OAuthPluginsTest.SM;
import com.mastfrog.acteur.auth.OAuthPlugins;
import com.mastfrog.acteur.auth.UniqueIDs;
import com.mastfrog.acteur.auth.UserFactory;
import com.mastfrog.acteur.auth.UserFactory.Slug;
import com.mastfrog.acteur.auth.UserInfo;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.client.HttpClient;
import java.util.HashMap;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author tim
 */
@RunWith(GuiceRunner.class)
@TestWith({SM.class, M.class})
public class OAuthPluginsTest {

    @Test
    public void sanity(UserFactory f) {
        Slug slug = f.newSlug("xx");
        Object o = f.newUser("foo", slug, "Foo", new HashMap<String, Object>());
        Set<String> s = f.getSlugNames(o);
        assertEquals(1, s.size());
        assertEquals("xx", s.iterator().next());

        Optional<Slug> foundo = f.getSlug("xx", o, false);
        assertTrue(foundo.isPresent());
        Slug found = foundo.get();

        assertEquals(slug, found);
    }

    @Test
    public void cookieDecoding(OAuthPlugins pg, UniqueIDs is) {
        String slug = is.newId();
        String encoded = pg.encodeCookieValue("foo@bar.com", slug);
        String[] parts = encoded.split(":");
        UserInfo info = pg.decodeCookieValue(encoded).get();
        assertEquals("foo@bar.com", info.userName);
        assertEquals(parts[0], info.hashedSlug);
    }

    static class SM extends ServerModule {

        public SM() {
            super(MockApp.class, 5, 5, 5);
        }

    }

    static class M extends AbstractModule {

        @Override
        protected void configure() {
            bind(FakeOAuthPlugin.class).asEagerSingleton();
            bind(UserFactory.class).to(MockUserFactory.class).in(Scopes.SINGLETON);
            bind(HttpClient.class).toInstance(HttpClient.builder()
                    .dontFollowRedirects()
                    .noCompression()
                    .build());

            bind(new TypeLiteral<UserFactory<?>>() {
            }).toProvider(UF.class);
        }

        static class UF implements Provider<UserFactory<?>> {

            private final Provider<UserFactory> del;

            @Inject
            public UF(Provider<UserFactory> del) {
                this.del = del;
            }

            @Override
            public UserFactory<?> get() {
                return del.get();
            }

        }
    }
}
