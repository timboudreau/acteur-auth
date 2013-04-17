package com.timboudreau.trackerapi;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.util.Realm;
import com.mastfrog.jackson.JacksonConfigurer;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import java.io.IOException;
import java.net.UnknownHostException;
import org.bson.types.ObjectId;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
final class MongoModule extends AbstractModule {

    private final Settings settings;

    MongoModule(Settings settings) {
        this.settings = settings;
    }

    @Override
    protected void configure() {
        bind(BasicDBObject.class).toProvider(EventToQuery.class);
        try {
            MongoClient mc = new MongoClient(settings.getString("mongoHost", "localhost"),
                    settings.getInt("mongoPort", 27017));
            bind(MongoClient.class).toInstance(mc);
            DB db = mc.getDB("timetracker");
            bind(DB.class).toInstance(db);
        } catch (UnknownHostException ex) {
            Exceptions.chuck(ex);
        }
        bind(Realm.class).toProvider(RealmProvider.class);
    }

    @Singleton
    static class RealmProvider implements Provider<Realm> {
        private static final Realm DEFAULT = Realm.createSimple("Timetracker");
        private final Provider<Application> app;
        @Inject
        RealmProvider(Provider<Application> app) {
            this.app = app;
        }

        @Override
        public Realm get() {
            // PENDING:  Let a resource inject a realm
            return DEFAULT;
        }

    }

    @ServiceProvider(service=JacksonConfigurer.class)
    public static final class JacksonC implements JacksonConfigurer {

        @Override
        public ObjectMapper configure(ObjectMapper om) {
            om.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
            SimpleModule sm = new SimpleModule("mongo", new Version(1, 0, 0, null, "com.timboudreau", "trackerapi"));
            sm.addSerializer(new C());
            om.registerModule(sm);
            return om;
        }

        static class C extends JsonSerializer<ObjectId> {

            @Override
            public Class<ObjectId> handledType() {
                return ObjectId.class;
            }

            @Override
            public void serialize(ObjectId t, JsonGenerator jg, SerializerProvider sp) throws IOException, JsonProcessingException {
                String id = t.toStringMongod();
                jg.writeString(id);
            }
        }
    }
}
