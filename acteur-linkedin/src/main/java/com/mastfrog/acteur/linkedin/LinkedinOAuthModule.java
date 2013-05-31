package com.mastfrog.acteur.linkedin;

import com.google.inject.AbstractModule;
import com.mastfrog.netty.http.client.HttpClient;

/**
 * Caveat:  Twitter does not support sending state as a callback.  So the
 * UserFactory has to return something useful from lookupLoginState("_nastyHack")
 * to get around this.
 *
 * @author Tim Boudreau
 */
public class LinkedinOAuthModule extends AbstractModule {

    public static final String SETTINGS_KEY_API_KEY = "linkedin.api.key";
    public static final String SETTINGS_KEY_SECRET_KEY = "linkedin.secret.key";

    @Override
    protected void configure() {
        bind(LinkedinOAuthPlugin.class).asEagerSingleton();
        bind(HttpClient.class).toInstance(HttpClient.builder()
                .followRedirects()
                .setUserAgent("twitter4j http://twitter4j.org/ /3.0.4-SNAPSHOT")
                .maxInitialLineLength(512)
                .threadCount(3)
                .build());
    }
}
