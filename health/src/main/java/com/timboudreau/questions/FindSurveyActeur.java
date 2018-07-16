package com.timboudreau.questions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.util.strings.Strings.sha1;
import com.mastfrog.util.time.TimeUtil;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.util.Date;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
final class FindSurveyActeur extends Acteur {

    @Inject
    public FindSurveyActeur(Page page, HttpEvent evt, @Named(value = "surveys") DBCollection questions, ObjectMapper mapper) throws JsonProcessingException, IOException {
        ObjectId id = new ObjectId(evt.path().getElement(3).toString());
        DBObject ob = questions.findOne(new BasicDBObject("_id", id));
        if (ob == null) {
            setState(new RespondWith(HttpResponseStatus.NOT_FOUND, "No id " + id));
            return;
        } else {
            next(ob);
        }
        String value = mapper.writeValueAsString(ob) + '\n';
        Date lm = (Date) ob.get("lastModified");
        add(Headers.LAST_MODIFIED, TimeUtil.fromUnixTimestamp(lm.toInstant().toEpochMilli()));
        add(Headers.ETAG, sha1(value));
        next(value, ob, id);
    }
}
