package com.mastfrog.acteur.auth;

import com.google.common.base.Optional;
import com.mastfrog.acteur.auth.MockUserFactory.MockUser;
import com.mastfrog.acteur.auth.OAuthPlugin;
import com.mastfrog.acteur.auth.OAuthPlugins;
import static com.mastfrog.acteur.auth.OAuthPlugins.DISPLAY_NAME_COOKIE_NAME;
import com.mastfrog.acteur.auth.UserFactory;
import com.mastfrog.acteur.auth.UserInfo;
import com.mastfrog.acteur.util.Headers;
import static com.mastfrog.acteur.util.Headers.COOKIE;
import com.mastfrog.acteur.util.PasswordHasher;
import com.mastfrog.acteur.util.Realm;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.client.StateType;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarness.CallResult;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultCookie;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.SEE_OTHER;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({OAuthPluginsTest.SM.class, OAuthPluginsTest.M.class, TestHarnessModule.class})
public class LiveAppTest {

    @Test
    public void testOAuth(TestHarness harness, OAuthPlugins plugins, UserFactory uf) throws Throwable {
        harness.get("sanity").go().assertStatus(OK).assertContent("SUCCESS");
        URI loc = harness.get("/" + plugins.getBouncePageBasePath() + "/fk")
                .log()
                .go()
                .assertStateSeen(StateType.Closed)
                .assertStatus(SEE_OTHER)
                .getHeader(Headers.LOCATION);

        String state = ((MockUserFactory) uf).states().iterator().next().state;
        assertEquals(loc.toString(), "http://localhost:3947/redirect?state=" + state);
        assertEquals(((MockUserFactory) uf).states() + "", 1, ((MockUserFactory) uf).states().size());
        System.out.println(((MockUserFactory) uf).states());

        assertTrue(loc.toString(),
                loc.toString().startsWith("http://localhost:"
                + 3947 + "/redirect"));

        CallResult res = harness.get(plugins.getLandingPageBasePath(), "fk").addQueryPair("state", state)
                .addQueryPair("redirect", "/foo/bar").go()
                .assertCode(302);
        res.assertHeader(Headers.LOCATION, new URI("/users/user2/index.html"));
        Iterable<Cookie> cookies = res.getHeaders(Headers.SET_COOKIE);
        assertNotNull(cookies);
        assertTrue(cookies.iterator().hasNext());

        Cookie authCookie = null;
        Cookie displayNameCookie = null;
        for (Cookie ck : cookies) {
            System.out.println("COOKIE " + ck.getName() + " " + ck.getValue());
            if ("fk".equals(ck.getName())) {
                authCookie = ck;
            }
            if (OAuthPlugins.DISPLAY_NAME_COOKIE_NAME.equals(ck.getName())) {
                displayNameCookie = ck;
            }
        }
        assertNotNull(authCookie);
        assertNotNull(displayNameCookie);
        assertTrue(authCookie.getValue().endsWith("user2"));
        assertEquals("User 2", displayNameCookie.getValue());

        Optional<UserInfo> infoo = plugins.decodeCookieValue(authCookie.getValue());
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

        Optional<UserFactory.Slug> savedSlug = uf.getSlug(plug.code(), usero.get(), false);
        assertTrue(savedSlug.isPresent());

        UserFactory.Slug saved = savedSlug.get();
        String encoded = plugins.encodeCookieValue(info.userName, saved.slug).split(":")[0];
        assertEquals(encoded, slug);

        Map[] m = harness.get("auths").go().assertStatus(OK).content(Map[].class);
        assertEquals(1, m.length);
        Map mm = m[0];
        assertEquals("fk", mm.get("code"));

        Realm authHeader = harness.get("boink").go().assertCode(401).getHeader(Headers.WWW_AUTHENTICATE);
        assertNotNull(authHeader);
        System.out.println("REALM " + authHeader);

        CallResult authed = harness.get("boink").addHeader(COOKIE, new Cookie[]{new DefaultCookie(authCookie.getName(), authCookie.getValue()),
            new DefaultCookie(displayNameCookie.getName(), displayNameCookie.getValue())}).go().assertStatus(OK);

        Iterable<Cookie> all = authed.getHeaders(Headers.SET_COOKIE);
        assertFalse("Should not redundantly set cookies, but got " + all, all.iterator().hasNext());

        CallResult testLogin = harness.get("testLogin")
                .addHeader(COOKIE, new Cookie[]{new DefaultCookie(authCookie.getName(), authCookie.getValue())})
                .go()
                .assertStatus(OK)
                .assertHasCookie(DISPLAY_NAME_COOKIE_NAME);

        TestLoginPage.Result login = testLogin.content(TestLoginPage.Result.class);
        assertTrue(login.success);
        assertEquals("/users/user2/index.html", login.homePage);
        String content = testLogin.content();
        System.out.println("CONTENT: " + content);
    }

    @Test
    public void testBasicAuth(TestHarness harness, OAuthPlugins plugins, UserFactory uf, PasswordHasher hasher) throws Throwable {
        String pw = hasher.encryptPassword("password");
        MockUser user = (MockUser) uf.newUser("joe", pw, "Joe Blow", Collections.emptyMap());
        assertNotNull(uf.getPasswordHash(user));
        System.out.println("CREATED " + user);

        CallResult res = harness.get("boink").basicAuthentication("joe", "password").go()
                .assertStatus(OK)
                .assertHasCookie(OAuthPlugins.DISPLAY_NAME_COOKIE_NAME)
                .assertCookieValue(OAuthPlugins.DISPLAY_NAME_COOKIE_NAME, "Joe Blow");

    }
}
