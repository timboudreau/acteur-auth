package com.timboudreau.questions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.AuthenticationActeur;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.util.CacheControl;
import com.timboudreau.trackerapi.support.AuthorizedChecker;
import java.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
public class GetSurveyResource extends Page {

    public static final String SURVEY_PATTERN = "^users/.*?/survey/.*?";

    @Inject
    GetSurveyResource(ActeurFactory af) {
        add(af.matchPath(SURVEY_PATTERN));
        add(af.matchMethods(Method.GET));
        add(AuthenticationActeur.class);
        add(AuthorizedChecker.class);
        add(FindSurveyActeur.class);
        add(af.sendNotModifiedIfETagHeaderMatches());
        add(af.sendNotModifiedIfIfModifiedSinceHeaderMatches());
        add(SurveyActeur.class);
    }

    @Override
    protected String getDescription() {
        return "Get a survey by id";
    }

    private static final class SurveyActeur extends Acteur {

        @Inject
        SurveyActeur(String value) throws JsonProcessingException {
            CacheControl c= new CacheControl(CacheControlTypes.Public, CacheControlTypes.must_revalidate).add(CacheControlTypes.max_age, Duration.ofMinutes(2));
            add(Headers.CACHE_CONTROL, c);
            ok(value);
        }
    }
}
