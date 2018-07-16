/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.acteur.mongo.userstore;

import com.google.common.base.Optional;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Names;
import com.mastfrog.acteur.auth.UniqueIDs;
import com.mastfrog.acteur.auth.UserFactory;
import com.mastfrog.acteur.auth.UserFactory.LoginState;
import com.mastfrog.acteur.auth.UserFactory.Slug;
import com.mastfrog.acteur.mongo.MongoInitializer;
import com.mastfrog.acteur.mongo.MongoModule;
import com.mastfrog.acteur.mongo.userstore.MongoUserFactoryTest.M;
import com.mastfrog.acteur.util.PasswordHasher;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.util.time.TimeUtil;
import static com.mastfrog.util.time.TimeUtil.nowGMT;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author tim
 */
@RunWith(GuiceRunner.class)
@TestWith({M.class, UniqueIDs.UniqueIdsModule.class})
public class MongoUserFactoryTest {

    @Test
    public void testIt(MongoDBRunner run, MongoUserFactory uf, PasswordHasher hasher) throws IOException, InterruptedException {
        assertFalse(uf.findUserByName("nobody").isPresent());
        String userName = "testUser";
        DBObject ob = uf.newUser(userName, hasher.hash("password"),
                "Test User", new HashMap<String, Object>());
        assertNotNull(ob);
        ob = uf.findUserByName(userName).get();
        assertNotNull(ob);
        TTUser u = (TTUser) uf.toUserObject(ob);
        assertNotNull(u);
        assertTrue(ob.get("name").getClass().getName(), ob.get("name") instanceof List);
        assertEquals(userName, ((List) ob.get("name")).get(0));
        assertEquals("Test User", ob.get("displayName"));
        assertEquals(0, ob.get("version"));
        assertEquals(userName, u.name());
        assertEquals(Arrays.asList(userName), u.names());
        assertEquals("Test User", u.displayName());
        assertEquals(0, u.version());

        Optional<Slug> slug = uf.getSlug("gg", ob, true);
        assertTrue(slug.isPresent());

        String slugValue = slug.get().slug;
        assertNotNull(slugValue);

        Optional<Slug> slug2 = uf.getSlug("gg", ob, true);
        assertTrue(slug2.isPresent());

        String slugValue2 = slug.get().slug;
        assertNotNull(slugValue2);
        assertEquals(slugValue, slugValue2);

        assertEquals(hasher.hash("password"), uf.getPasswordHash(ob).get());
        assertFalse(uf.getAccessToken(ob, "foo").isPresent());
        uf.putAccessToken(ob, "bar", "foo");
        ob = uf.findUserByName(userName).get();
        assertTrue(uf.getAccessToken(ob, "foo").isPresent());
        assertEquals("bar", uf.getAccessToken(ob, "foo").get());


        LoginState ls = new LoginState("foo", "/foo/bar");
        uf.saveLoginState(ls);
        Optional<LoginState> ls2o = uf.lookupLoginState("foo");
        assertTrue(ls2o.isPresent());
        LoginState ls2 = ls2o.get();
        assertEquals(ls, ls2);
        assertEquals(ls.redirectTo, ls2.redirectTo);

        ZonedDateTime now = nowGMT().with(ChronoField.MICRO_OF_SECOND, 0);
        uf.putSlug(ob, new Slug("gg", "xyz", TimeUtil.toUnixTimestamp(now)));
        ob = uf.findUserByName(userName).get();

        Optional<Slug> slug3 = uf.getSlug("gg", ob, true);
        assertTrue(slug3.isPresent());
        Slug s = slug3.get();
        assertEquals("gg", s.name);
        assertEquals("xyz", s.slug);
        assertEquals(now, s.created());
    }

    static class M extends AbstractModule {

        @Override
        protected void configure() {
            bind(Initializer.class).asEagerSingleton();
            bind(UserFactory.class).to(MongoUserFactory.class);
            bind(Charset.class).toInstance(CharsetUtil.UTF_8);
            bind(String.class).annotatedWith(Names.named("application"))
                    .toInstance(MongoUserFactoryTest.class.getSimpleName());
            install(new MongoModule("testit")
                    .bindCollection("users", "ttusers")
                    .bindCollection("login"));
        }
    }

    static class Initializer extends MongoInitializer {

        volatile boolean created;
        Set<String> onCreateCalled = new HashSet<>();
        Set<String> onBeforeCreateCalled = new HashSet<>();

        @Inject
        public Initializer(Registry registry) {
            super(registry);
        }

        @Override
        protected void onMongoClientCreated(MongoClient client) {
            created = true;
        }

        @Override
        protected void onCreateCollection(DBCollection collection) {
            System.out.println("On create " + collection.getName());
            onCreateCalled.add(collection.getName());
        }

        @Override
        protected void onBeforeCreateCollection(String name, BasicDBObject params) {
            onBeforeCreateCalled.add(name);
            switch (name) {
                case "login":
                    params.append("capped", true).append("size", 10000).append("max", 1000);
            }
        }
    }
}
