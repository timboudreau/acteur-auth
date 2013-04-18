package com.timboudreau.questions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.WriteResult;
import com.timboudreau.trackerapi.Timetracker;
import com.timboudreau.trackerapi.support.Auth;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
public class DeleteSurveyResource extends Page {

    @Inject
    DeleteSurveyResource(ActeurFactory af) {
        add(af.matchPath(GetSurveyResource.SURVEY_PATTERN));
        add(af.matchMethods(Method.GET));
        add(Auth.class);
        add(FindSurveyActeur.class);
        add(DeleteSurveyActeur.class);
    }

    @Override
    protected String getDescription() {
        return "Get a survey by id";
    }

    private static final class DeleteSurveyActeur extends Acteur {

        @Inject
        DeleteSurveyActeur(Event evt, @Named("surveys") DBCollection surveys) throws JsonProcessingException {
            try {
                ObjectId id = new ObjectId(evt.getPath().getElement(3).toString());
                WriteResult res = surveys.remove(new BasicDBObject("_id", id));
                setState(new RespondWith(HttpResponseStatus.OK, Timetracker.quickJson("deleted", res.getN())));
            } catch (IllegalArgumentException e) {
                setState(new RespondWith(HttpResponseStatus.BAD_REQUEST, "Not a valid ID: " + evt.getPath().getElement(3)));
            }
        }
    }
}
