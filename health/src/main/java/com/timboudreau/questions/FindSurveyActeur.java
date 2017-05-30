package com.timboudreau.questions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.util.Streams;
import static com.mastfrog.util.Strings.sha1;
import com.mastfrog.util.streams.HashingInputStream;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Date;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;

/**
 *
 * @author Tim Boudreau
 */
final class FindSurveyActeur extends Acteur {

    @Inject
    public FindSurveyActeur(Page page, HttpEvent evt, @Named(value = "surveys") DBCollection questions, ObjectMapper mapper) throws JsonProcessingException, IOException {
        ObjectId id = new ObjectId(evt.getPath().getElement(3).toString());
        DBObject ob = questions.findOne(new BasicDBObject("_id", id));
        if (ob == null) {
            setState(new RespondWith(HttpResponseStatus.NOT_FOUND, "No id " + id));
            return;
        } else {
            next(ob);
        }
        String value = mapper.writeValueAsString(ob) + '\n';
        Date lm = (Date) ob.get("lastModified");
        add(Headers.LAST_MODIFIED, new DateTime(lm));
        add(Headers.ETAG, sha1(value));
        next(value, ob, id);
    }
}
