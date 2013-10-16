package com.timboudreau.trackerapi;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.auth.ActeurAuthModule;
import com.mastfrog.acteur.auth.HomePageRedirector;
import com.mastfrog.acteur.auth.UserFactory;
import com.mastfrog.acteur.facebook.auth.FacebookOAuthModule;
import com.mastfrog.acteur.google.auth.GoogleOAuthModule;
import com.mastfrog.acteur.mongo.MongoInitializer;
import com.mastfrog.acteur.mongo.MongoModule;
import com.mastfrog.acteur.mongo.userstore.MongoUserFactory;
import static com.mastfrog.acteur.mongo.userstore.MongoUserFactory.USERS_COLLECTION_NAME;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.acteur.util.Realm;
import com.mastfrog.settings.Settings;
import com.mongodb.BasicDBObject;
import com.timboudreau.questions.QuestionsModule;
import java.io.IOException;
import java.text.SimpleDateFormat;
import org.joda.time.DateTime;

/**
 *
 * @author Tim Boudreau
 */
final class TimetrackerAppModule extends AbstractModule {

    private final Settings settings;

    TimetrackerAppModule(Settings settings) {
        this.settings = settings;
    }

    @Override
    protected void configure() {
        bind(Realm.class).toInstance(Realm.createSimple(Timetracker.REALM_NAME));

        String userCollectionName = settings.getString("user.collection.name", "ttusers");
        MongoModule mongoModule = new MongoModule("timetracker")
                .bindCollection("events")
                .bindCollection("login")
                .bindCollection(USERS_COLLECTION_NAME, userCollectionName);

        bind(BasicDBObject.class).toProvider(EventToQuery.class);

        install(new QuestionsModule());
        install(mongoModule);
        install(new GoogleOAuthModule());
        install(new FacebookOAuthModule());
        install(new ActeurAuthModule(MongoUserFactory.class));
        bind(Ini.class).asEagerSingleton();
        bind(HomePageRedirector.class).to(HPR.class);
    }

    private static class HPR extends HomePageRedirector {

        private final PathFactory paths;
        @Inject
        HPR(PathFactory paths) {
            this.paths = paths;
        }

        @Override
        public <T> String getRedirectURI(UserFactory<T> uf, T user, Event<?> evt) {
            String nm = uf.getUserName(user);
            return "/users/" + nm + "/";
        }
    }

    private static class BsonDateSerializer extends StdSerializer<DateTime> {

        BsonDateSerializer() {
            super(DateTime.class);
        }

        @Override
        public void serialize(DateTime t, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonGenerationException {
            jgen.writeStartObject();
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z");
            jgen.writeString(fmt.format(t.toDate()));
        }
    }

    private static class BsonDateDeserializer extends JsonDeserializer<DateTime> {

        @Override
        public DateTime deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JsonProcessingException {
            JsonNode tree = jp.readValueAsTree();
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z");
            try {
                return new DateTime(fmt.parse(tree.get("$date").textValue()));
            } catch (Exception e) {
                throw new IOException("Bad date " + tree.get("$date"));
            }
        }
    }

    static class Ini extends MongoInitializer {

        @Inject
        public Ini(MongoInitializer.Registry registry) {
            super(registry);
        }

        @Override
        protected void onBeforeCreateCollection(String name, BasicDBObject params) {
            switch (name) {
                case "events":
                    params.append("capped", true)
                            .append("size", 100000)
                            .append("max", 30);
                    break;
                case "auth":
                    params.append("capped", true)
                            .append("size", 1000000)
                            .append("max", 1000);
                    break;
                case "login":
                    params.append("capped", true)
                            .append("size", 1000000)
                            .append("max", 1000);
                    break;
            }
        }
    }
}
