/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.mastfrog.acteur;

import com.google.common.base.Optional;
import com.google.inject.AbstractModule;
import com.mastfrog.acteur.PluginsTest.M;
import com.mastfrog.acteur.auth.OAuthPlugin;
import com.mastfrog.acteur.auth.OAuthPlugins;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.runner.RunWith;

/**
 *
 * @author tim
 */
@RunWith(GuiceRunner.class)
public class PluginsTest {

    @TestWith(M.class)
    public void test(OAuthPlugins plugins) {
        assertTrue(true);
        assertNotNull(plugins);
        Optional<OAuthPlugin<?>> pgno = plugins.find("fk");
        assertTrue(pgno.isPresent());
        OAuthPlugin pgn = pgno.get();
        assertNotNull(pgn);
        assertTrue(pgn instanceof FakeOAuthPlugin);
        OAuthPlugins.PluginInfo info = plugins.getPlugins().iterator().next();
        assertEquals("api/foo/oauth/fk", info.loginPagePath);
    }

    static class M extends AbstractModule {

        @Override
        protected void configure() {
            bind(FakeOAuthPlugin.class).asEagerSingleton();
        }
    }
}
