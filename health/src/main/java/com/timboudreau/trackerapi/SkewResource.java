package com.timboudreau.trackerapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.Method;
import com.timboudreau.trackerapi.support.Auth;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
public class SkewResource extends Page {

    private final long now = System.currentTimeMillis();

    @Inject
    SkewResource(ActeurFactory af, Event evt, Provider<ObjectMapper> mapper) throws IOException {
        add(af.matchPath("^skew$"));
        add(af.matchMethods(Method.GET));
        add(Auth.class);
        Optional<Long> l = evt.getLongParameter("time");
        Map<String, Object> m = new HashMap<>();
        m.put("received", now);
        if (l.isPresent()) {
            long remote = l.get();
            m.put("sent", remote);
            m.put("offset", now - remote);
        }
        add(af.respondWith(HttpResponseStatus.OK, mapper.get().writeValueAsString(m)));
    }

    @Override
    protected String getDescription() {
        return "Determine Clock Skew and Request Round trip Time";
    }
}
