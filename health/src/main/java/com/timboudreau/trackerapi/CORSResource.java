package com.timboudreau.trackerapi;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.ACCEPT;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.acteur.headers.Headers.X_REQUESTED_WITH;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.preconditions.Methods;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
@Methods(Method.OPTIONS)
final class CORSResource extends Page {

    @Inject
    CORSResource(ActeurFactory af) {
        add(CorsHeaders.class);
    }

    private static final class CorsHeaders extends Acteur {

        @Inject
        CorsHeaders(HttpEvent evt) {
            add(Headers.ACCESS_CONTROL_ALLOW_ORIGIN.toStringHeader(), "*");
            add(Headers.ACCESS_CONTROL_ALLOW, new Method[]{Method.GET, Method.POST, Method.PUT, Method.DELETE, Method.OPTIONS});
            add(Headers.ACCESS_CONTROL_ALLOW_HEADERS, new HeaderValueType<?>[] {CONTENT_TYPE, ACCEPT, X_REQUESTED_WITH});
            add(Headers.ACCESS_CONTROL_ALLOW_CREDENTIALS, true);
            add(Headers.ACCESS_CONTROL_MAX_AGE, Duration.ofSeconds(600));
            add(Headers.CACHE_CONTROL, CacheControl.$(CacheControlTypes.Public).add(CacheControlTypes.max_age, Duration.ofDays(365)));
            add(Headers.CONTENT_LENGTH, 0L);
            setState(new RespondWith(HttpResponseStatus.NO_CONTENT));
        }
    }
}
