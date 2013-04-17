package com.timboudreau.trackerapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Acteur.RespondWith;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.Method;
import com.mongodb.DB;
import static com.timboudreau.trackerapi.Properties.type;
import com.timboudreau.trackerapi.support.Auth;
import com.timboudreau.trackerapi.support.AuthorizedChecker;
import com.timboudreau.trackerapi.support.CreateCollectionPolicy;
import com.timboudreau.trackerapi.support.TTUser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
public class SetsResource extends Page {

    public static final String PAT = "^users/(.*?)/list$";

    @Inject
    public SetsResource(ActeurFactory af) {
        add(af.matchPath(PAT));
        add(af.matchMethods(Method.GET, Method.HEAD));
        add(af.banParameters(type));
        add(Auth.class);
        add(AuthorizedChecker.class);
        add(CreateCollectionPolicy.DONT_CREATE.toActeur());
        add(ListSetsActeur.class);
    }

    @Override
    protected String getDescription() {
        return "List user's sets of data";
    }

    private static class ListSetsActeur extends Acteur {

        @Inject
        ListSetsActeur(Event evt, DB db, TTUser user, ObjectMapper mapper) throws IOException {
            List<String> l = new ArrayList<>();
            String pfix = new StringBuilder(evt.getPath().getElement(1).toString()).append('_').toString();
            for (String coll : db.getCollectionNames()) {
                if (coll.startsWith(pfix)) {
                    l.add(coll.substring(pfix.length()));
                }
            }
            Collections.sort(l);
            if (evt.getMethod() == Method.HEAD) {
                setState(new RespondWith(200));
            } else {
                setState(new RespondWith(200, mapper.writeValueAsString(l)));
            }
        }
    }
}
