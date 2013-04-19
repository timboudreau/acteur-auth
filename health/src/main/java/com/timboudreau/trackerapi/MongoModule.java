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
import com.google.inject.Provider;
import com.google.inject.name.Names;
import com.mastfrog.acteur.util.Realm;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.timboudreau.questions.QuestionsModule;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import org.joda.time.DateTime;

/**
 *
 * @author Tim Boudreau
 */
final class MongoModule extends AbstractModule {
    private final Settings settings;
    private final Map<String,String> nameForCollection = new HashMap<>();

    MongoModule(Settings settings) {
        this.settings = settings;
    }
    
    public MongoModule addNamedCollectionBinding(String collectionName, String bindingName) {
        nameForCollection.put(bindingName, collectionName);
        return this;
    }

    @Override
    protected void configure() {
        bind(BasicDBObject.class).toProvider(EventToQuery.class);
        try {
            MongoClient mc = new MongoClient(
                    settings.getString("mongoHost", "localhost"),
                    settings.getInt("mongoPort", 27017));
            
            
            bind(MongoClient.class).toInstance(mc);
            DB db = mc.getDB("timetracker");
            
            bind(DB.class).toInstance(db);
            Provider<DB> dbProvider = binder().getProvider(DB.class);
            for (Map.Entry<String,String> e : nameForCollection.entrySet()) {
                bind(DBCollection.class).annotatedWith(Names.named(e.getValue()))
                        .toProvider(
                        new CollectionProvider(e.getKey(), dbProvider));
            }
        } catch (UnknownHostException ex) {
            Exceptions.chuck(ex);
        }
        bind(Realm.class).toInstance(Realm.createSimple(Timetracker.REALM_NAME));
        
        install(new QuestionsModule());
    }

    static class CollectionProvider implements Provider<DBCollection> {
        private final String name;
        private final Provider<DB> db;

        public CollectionProvider(String name, Provider<DB> db) {
            this.name = name;
            this.db = db;
        }

        @Override
        public DBCollection get() {
            return db.get().getCollection(name);
        }
    }
    
    static class BsonDateSerializer extends StdSerializer<DateTime> {
        
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
    
    static class BsonDateDeserializer extends JsonDeserializer<DateTime> {
        

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
    
}
