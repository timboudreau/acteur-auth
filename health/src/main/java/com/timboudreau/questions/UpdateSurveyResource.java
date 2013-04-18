package com.timboudreau.questions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.timboudreau.trackerapi.support.Auth;
import com.timboudreau.trackerapi.support.TTUser;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.HashMap;
import java.util.Map;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
public class UpdateSurveyResource extends Page {

    @Inject
    UpdateSurveyResource(ActeurFactory af) {
        add(af.matchPath(GetSurveyResource.SURVEY_PATTERN));
        add(af.matchMethods(Method.POST));
        add(MustHaveSomeParameters.class);
        add(af.banParameters("lastModified", "createdBy", "version", "created"));
        add(Auth.class);
        add(FindSurveyActeur.class);
        add(UserMustBeCreator.class);
        add(UpdateSurveyActeur.class);
    }

    private static class UserMustBeCreator extends Acteur {

        @Inject
        UserMustBeCreator(TTUser user, DBObject obj) {
            Object id = obj.get("createdBy");
            if (!user.id.equals(id) && id != null) {
                setState(new RespondWith(HttpResponseStatus.FORBIDDEN, 
                        "Not created by " + user.name + " but by " + id));
            } else {
                setState(new ConsumedState());
            }
        }
    }

    private static class MustHaveSomeParameters extends Acteur {

        @Inject
        MustHaveSomeParameters(Event evt) {
            if (evt.getParametersAsMap().isEmpty()) {
                setState(new RespondWith(HttpResponseStatus.BAD_REQUEST,
                        "Must have URL parameters for what to change"));
            }
            setState(new ConsumedState());
        }
    }

    @Override
    protected String getDescription() {
        return "Get a survey by id";
    }

    private static final class UpdateSurveyActeur extends Acteur {

        @Inject
        UpdateSurveyActeur(Event evt, DBObject obj, @Named("surveys") DBCollection surveys,
                ObjectMapper mapper) throws JsonProcessingException {

            ObjectId id = new ObjectId(evt.getPath().getElement(3).toString());
            BasicDBObject query = new BasicDBObject("_id", id);
            DBObject ob = surveys.findOne(query);
            if (ob == null) {
                setState(new RespondWith(HttpResponseStatus.NOT_FOUND, "No id " + id));
            } else {
                BasicDBObject edit = createEditFrom(evt, id);
                DBObject result = surveys.findAndModify(query, edit);
                setState(new RespondWith(HttpResponseStatus.ACCEPTED, mapper.writeValueAsString(result)));
            }
        }

        private BasicDBObject createEditFrom(Event evt, ObjectId id) {
            Map<String, Object> m = new HashMap<String, Object>(evt.getParametersAsMap());

            BasicDBObject result = new BasicDBObject();
            BasicDBObject set = new BasicDBObject();
            result.append("$set", set);
            for (Map.Entry<String, Object> e : m.entrySet()) {
                set.put(e.getKey(), e.getValue());
            }
            BasicDBObject inc = new BasicDBObject("version", 1);
            result.append("$inc", inc);

            return result;
        }
    }
}
