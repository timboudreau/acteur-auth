package com.timboudreau.questions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.AuthenticationActeur;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.mongo.userstore.TTUser;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.url.Path;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.timboudreau.questions.pojos.Survey;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import org.joda.time.DateTime;

/**
 *
 * @author Tim Boudreau
 */
public class AddSurveyResource extends Page {

    public static final String QUESTION_PATTERN = "^users/(.*?)/surveys";

    @Inject
    AddSurveyResource(ActeurFactory af) {
        add(af.matchPath(QUESTION_PATTERN));
        add(af.matchMethods(Method.PUT, Method.POST));
        add(AuthenticationActeur.class);
        add(SurveyWriter.class);
    }

    @Override
    protected String getDescription() {
        return "Add a survey";
    }

    private static final class SurveyWriter extends Acteur {

        @Inject
        @SuppressWarnings("unchecked")
        private SurveyWriter(Survey survey, @Named("surveys") DBCollection collection,
                ObjectMapper mapper, PathFactory pf, TTUser user) throws IOException, URISyntaxException {

            Map<String, Object> m = mapper.readValue(mapper.writeValueAsString(survey), Map.class);
            DateTime now = DateTime.now();
            m.put("createdBy", user.id());
            m.put("created", now.toDate());
            m.put("lastModified", now.toDate());
            m.put("version", 0);

            BasicDBObject ob = new BasicDBObject(m);
            collection.save(ob);
            URI uri = new URI(pf.toExternalPath(Path.parse("users/"
                    + user.name() + "/survey/"
                    + ob.getString("_id"))).toStringWithLeadingSlash());
            add(Headers.LOCATION, uri);
            setState(new RespondWith(HttpResponseStatus.SEE_OTHER, "Created "
                    + ob.getString("_id")));
        }
    }
}
