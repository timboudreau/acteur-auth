package com.mastfrog.acteur;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.mastfrog.acteur.Acteur.RespondWith;
import com.mastfrog.acteur.MockApp.Success;
import com.mastfrog.acteur.MockUserFactory.MockUser;
import com.mastfrog.acteur.OAuthPluginsTest.M;
import com.mastfrog.acteur.OAuthPluginsTest.SM;
import com.mastfrog.acteur.auth.OAuthPlugin;
import com.mastfrog.acteur.auth.OAuthPlugins;
import com.mastfrog.acteur.auth.UniqueIDs;
import com.mastfrog.acteur.auth.UserFactory;
import com.mastfrog.acteur.auth.UserFactory.Slug;
import com.mastfrog.acteur.auth.UserInfo;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

    @Test
    public void testApp(MockApp app, OAuthPlugins plugins) throws Throwable {
        UserFactory uf = app.uf;
        MockEvent evt = new MockEvent("/sanity");
        assertNotNull(app.hook);

        assertEquals(0, ((MockUserFactory) uf).states().size());

        assertTrue(app.iterator().hasNext());
        for (Page p : app) {
            System.out.println("PAGE " + p);
        }
        app.event(evt);

        app.hook.await();
        assertTrue(app.hook.acteur instanceof Success);
        assertTrue(app.hook.state instanceof RespondWith);
        assertTrue(app.hook.page instanceof MockApp.SanityCheckPage);
        assertEquals(HttpResponseStatus.OK, app.hook.status);
        assertEquals("SUCCESS", evt.getWrittenResponse());

        MockEvent outbound = new MockEvent("/" + plugins.getBouncePageBasePath() + "/fk");
        app.event(outbound);

        app.hook.await();
        assertNotNull(app.hook.response);

        assertEquals(HttpResponseStatus.SEE_OTHER, app.hook.response.getStatus());
        String loc = ((DefaultFullHttpResponse) app.hook.response).headers().get("Location");
        assertTrue(loc, loc.startsWith("http://localhost:3947/redirect"));
        String state = ((MockUserFactory) uf).states().iterator().next().state;
        assertEquals(loc, "http://localhost:3947/redirect?state=" + state);

        assertEquals(((MockUserFactory) uf).states() + "", 1, ((MockUserFactory) uf).states().size());
        System.out.println(((MockUserFactory) uf).states());

        MockEvent inbound = new MockEvent("/" + plugins.getLandingPageBasePath() + "/fk").addParameter("state", state).addParameter("redirect", "/foo/bar");
        app.event(inbound);
        app.hook.await();

        System.out.println(app.hook.response);

        assertEquals(302, app.hook.response.getStatus().code());
        loc = ((DefaultFullHttpResponse) app.hook.response).headers().get("Location");
        assertEquals("/", loc);
        List<String> cookies = ((DefaultFullHttpResponse) app.hook.response).headers().getAll(HttpHeaders.Names.SET_COOKIE);
        List<Cookie> cks = new LinkedList<>();
        Cookie ours = null;
        for (String s : cookies) {
            Set<Cookie> ck = CookieDecoder.decode(s);
            cks.addAll(ck);
            for (Cookie k : ck) {
                if (k.getName().equals("fk")) {
                    ours = k;
                }
            }
        }
        assertNotNull(ours);
        Optional<UserInfo> infoo = plugins.decodeCookieValue(ours.getValue());
        assertTrue(infoo.isPresent());

        UserInfo info = infoo.get();
        assertNotNull(info);
        assertEquals("user2", info.userName);

        String slug = info.hashedSlug;

        Optional<OAuthPlugin<?>> plugo = plugins.find("fk");
        assertTrue(plugo.isPresent());

        OAuthPlugin<?> plug = plugo.get();

        Optional<?> usero = uf.findUserByName(info.userName);
        assertTrue(usero.isPresent());

        Optional<Slug> savedSlug = uf.getSlug(plug.code(), usero.get(), false);
        assertTrue(savedSlug.isPresent());

        Slug saved = savedSlug.get();
        String encoded = plugins.encodeCookieValue(info.userName, saved.slug).split(":")[0];
        assertEquals(encoded, slug);

        MockEvent list = new MockEvent("/auths");
        app.event(list);
        app.hook.await();
        Thread.sleep(1000);
        String listOfStuff = list.getWrittenResponse();

        Map[] m = new ObjectMapper().readValue(listOfStuff, Map[].class);
        assertEquals(1, m.length);
        Map mm = m[0];
        assertEquals("fk", mm.get("code"));

        MockEvent auth = new MockEvent("/boink");
        app.event(auth);
        app.hook.await();
        assertEquals(401, app.hook.response.getStatus().code());
        assertNotNull(app.hook.getResponseHeader(Headers.WWW_AUTHENTICATE));

        auth = new MockEvent("/boink");
        DefaultCookie nue = new DefaultCookie(ours.getName(), ours.getValue());
        auth.add(Headers.COOKIE, new Cookie[]{nue});
        app.event(auth);
        app.hook.await();
        assertEquals(app.hook.response + "", 200, app.hook.response.getStatus().code());
        assertEquals("SUCCESS user2", auth.getWrittenResponse());
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
