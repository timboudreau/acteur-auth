package com.timboudreau.trackerapi.support;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.util.Method;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.net.URISyntaxException;

/**
 *
 * @author tim
 */
public class GoogleLoginPage extends Page {
    
    @Inject
    GoogleLoginPage(ActeurFactory af) {
        add(af.matchPath("^google$"));
        add(af.matchMethods(Method.GET, Method.POST, Method.PUT));
//        add(af.requireParameters("dest"));
        add(RedirectActeur.class);
    }
    
    private static final class RedirectActeur extends Acteur {
        @Inject
        RedirectActeur(Event evt, GoogleAuth ga) throws URISyntaxException {
            String referrer = evt.getHeader(Headers.stringHeader("Referer"));
            if (referrer == null) {
                referrer = "/";
            }
            String redirectTo = ga.getRedirectURL(referrer);
            add(Headers.LOCATION, new URI(redirectTo));
            setState(new RespondWith(HttpResponseStatus.FOUND));
        }
    }
}
