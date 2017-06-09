package com.timboudreau.questions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.AuthenticationActeur;
import com.mastfrog.acteur.headers.Method;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.PathRegex;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.WriteResult;
import static com.timboudreau.questions.GetSurveyResource.SURVEY_PATTERN;
import com.timboudreau.trackerapi.Timetracker;
import com.timboudreau.trackerapi.support.AuthorizedChecker;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
@Methods(GET)
@PathRegex(SURVEY_PATTERN)
public class DeleteSurveyResource extends Page {

    @Inject
    DeleteSurveyResource(ActeurFactory af) {
        add(AuthenticationActeur.class);
        add(AuthorizedChecker.class);
        add(FindSurveyActeur.class);
        add(DeleteSurveyActeur.class);
    }

    @Override
    protected String getDescription() {
        return "Get a survey by id";
    }

    private static final class DeleteSurveyActeur extends Acteur {

        @Inject
        DeleteSurveyActeur(HttpEvent evt, @Named("surveys") DBCollection surveys) throws JsonProcessingException, UnsupportedEncodingException {
            try {
                String otherUserNameOrID = evt.path().getElement(3).toString();
                otherUserNameOrID = URLDecoder.decode(otherUserNameOrID, "UTF-8");
                ObjectId id = new ObjectId(otherUserNameOrID);
                WriteResult res = surveys.remove(new BasicDBObject("_id", id));
                setState(new RespondWith(HttpResponseStatus.OK, Timetracker.quickJson("deleted", res.getN())));
            } catch (IllegalArgumentException e) {
                setState(new RespondWith(HttpResponseStatus.BAD_REQUEST, "Not a valid ID: " + evt.path().getElement(3)));
            }
        }
    }
}
