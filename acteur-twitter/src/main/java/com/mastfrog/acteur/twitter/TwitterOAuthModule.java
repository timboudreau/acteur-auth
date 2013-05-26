package com.mastfrog.acteur.twitter;

import com.google.inject.AbstractModule;
import com.mastfrog.netty.http.client.HttpClient;

/**
 * Caveat:  Twitter does not support sending state as a callback.  So the
 * UserFactory has to return something useful from lookupLoginState("_nastyHack")
 * to get around this.
 *
 * @author Tim Boudreau
 */
public class TwitterOAuthModule extends AbstractModule {

    public static final String SETTINGS_KEY_CONSUMER_SECRET = "twitter.consumer.secret";
    public static final String SETTINGS_KEY_CONSUMER_key = "twitter.consumer.key";

    @Override
    protected void configure() {
        bind(TwitterOAuthPlugin.class).asEagerSingleton();
        bind(HttpClient.class).toInstance(HttpClient.builder()
                .followRedirects()
                .setUserAgent("twitter4j http://twitter4j.org/ /3.0.4-SNAPSHOT")
                .maxInitialLineLength(512)
                .threadCount(3)
                .build());
    }
}
