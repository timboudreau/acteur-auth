package com.mastfrog.acteur.auth;

import com.google.common.base.Optional;
import com.mastfrog.acteur.auth.MockUserFactory.MockUser;
import static com.mastfrog.acteur.auth.OAuthPlugins.DISPLAY_NAME_COOKIE_NAME;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.COOKIE_B;
import com.mastfrog.acteur.util.PasswordHasher;
import com.mastfrog.acteur.util.Realm;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.client.StateType;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarness.CallResult;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
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
    @SuppressWarnings("unchecked")
    public void testOAuth(TestHarness harness, OAuthPlugins plugins, UserFactory uf) throws Throwable {
        harness.get("sanity").go().assertStatus(OK).assertContent("SUCCESS");
        URI loc = harness.get("/" + plugins.getBouncePageBasePath() + "/fk")
                .log()
                .go()
                .assertStateSeen(StateType.FullContentReceived)
                .assertStatus(SEE_OTHER)
                .getHeader(Headers.LOCATION);

        String state = ((MockUserFactory) uf).states().iterator().next().state;
        assertEquals(loc.toString(), "http://127.0.0.1:3947/redirect?state=" + state);
        assertEquals(((MockUserFactory) uf).states() + "", 1, ((MockUserFactory) uf).states().size());
        System.out.println(((MockUserFactory) uf).states());

        assertTrue(loc.toString(),
                loc.toString().startsWith("http://127.0.0.1:"
                + 3947 + "/redirect"));

        CallResult res = harness.get(plugins.getLandingPageBasePath(), "fk").addQueryPair("state", state)
                .addQueryPair("redirect", "/foo/bar").go()
                .assertCode(302);
        res.assertHeader(Headers.LOCATION, new URI("/users/user2/index.html"));

        System.out.println("\n\n***************\nGET COOKIES");
        Iterable<Cookie> cookies = res.getHeaders(Headers.SET_COOKIE_B);
        assertNotNull(cookies);
        assertTrue(cookies.iterator().hasNext());

        System.out.println("\n\n***************\nGET FK");
        Cookie authCookie = res.getCookieB("fk");
        System.out.println("\n\n***************\nGET DN");
        Cookie displayNameCookie = res.getCookieB(OAuthPlugins.DISPLAY_NAME_COOKIE_NAME);

        for (Cookie ck : cookies) {
            System.out.println("COOKIE '" + ck.name() + "' '" + ck.value() + "'");
//            if ("fk".equals(ck.name())) {
//                authCookie = ck;
//            }
            if (OAuthPlugins.DISPLAY_NAME_COOKIE_NAME.equals(ck.name())) {
                displayNameCookie = ck;
            }
        }

        assertNotNull("A display name cookie was not found", displayNameCookie);
        assertNotNull("A cookie named fk was not found", authCookie);
        assertTrue(authCookie.value().endsWith("user2"));
        assertEquals("User 2", displayNameCookie.value());

        Optional<UserInfo> infoo = plugins.decodeCookieValue(authCookie.value());
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

        Map[] m = harness.get("authtypes").go().assertStatus(OK).content(Map[].class);
        assertEquals(1, m.length);
        Map mm = m[0];
        assertEquals("fk", mm.get("code"));

        Realm authHeader = harness.get("boink").go().assertCode(401).getHeader(Headers.WWW_AUTHENTICATE);
        assertNotNull(authHeader);
        System.out.println("REALM " + authHeader);

        CallResult authed = harness.get("boink").addHeader(COOKIE_B, new Cookie[]{new DefaultCookie(authCookie.name(), authCookie.value()),
            new DefaultCookie(displayNameCookie.name(), displayNameCookie.value())}).go().assertStatus(OK);

//        Iterable<Cookie> all = authed.getHeaders(Headers.SET_COOKIE_B);
//        assertFalse("Should not redundantly set cookies, but got " + all, all.iterator().hasNext());

        CallResult testLogin = harness.get("testLogin")
                .addHeader(COOKIE_B, new Cookie[]{new DefaultCookie(authCookie.name(), authCookie.value())})
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
    @SuppressWarnings("unchecked")
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
