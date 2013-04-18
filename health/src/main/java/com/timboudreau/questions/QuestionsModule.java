package com.timboudreau.questions;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Names;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.timboudreau.questions.pojos.Survey;

/**
 *
 * @author Tim Boudreau
 */
public class QuestionsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Survey.class).toProvider(SurveyProvider.class);
        bind(DBCollection.class).annotatedWith(Names.named("surveys"))
                .toProvider(new CollectionProvider(binder()
                .getProvider(DB.class), "surveys"));
    }

    private static class CollectionProvider implements Provider<DBCollection> {

        private final Provider<DB> db;
        private final String name;

        @Inject
        public CollectionProvider(Provider<DB> db, String name) {
            this.db = db;
            this.name = name;
        }

        @Override
        public DBCollection get() {
            return db.get().getCollection(name);
        }
    }
}
