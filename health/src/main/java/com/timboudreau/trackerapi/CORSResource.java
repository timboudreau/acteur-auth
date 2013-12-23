package com.timboudreau.trackerapi;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.joda.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
final class CORSResource extends Page {

    @Inject
    CORSResource(ActeurFactory af) {
        add(af.matchMethods(Method.OPTIONS));
        add(CorsHeaders.class);
        getResponseHeaders().setContentLength(0);
    }

    private static final class CorsHeaders extends Acteur {

        @Inject
        CorsHeaders(HttpEvent evt) {
            add(Headers.stringHeader("Access-Control-Allow-Origin"), "*");
            add(Headers.ACCESS_CONTROL_ALLOW, new Method[]{Method.GET, Method.POST, Method.PUT, Method.DELETE, Method.OPTIONS});
            add(Headers.stringHeader("Access-Control-Allow-Headers"), "content-type, accept, X-Requested-With");
            add(Headers.stringHeader("Access-Control-Allow-Credentials"), "true");
            add(Headers.stringHeader("Access-Control-Allow-Max-Age"), "600");
            add(Headers.CACHE_CONTROL, CacheControl.$(CacheControlTypes.Public).add(CacheControlTypes.max_age, Duration.standardDays(365)));
            setState(new RespondWith(HttpResponseStatus.NO_CONTENT));
        }
    }
}
