package com.timboudreau.questions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.AuthenticationActeur;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.util.Method;
import com.timboudreau.trackerapi.support.AuthorizedChecker;
import org.joda.time.Duration;

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
        getReponseHeaders().addCacheControl(CacheControlTypes.Public);
        getReponseHeaders().addCacheControl(CacheControlTypes.must_revalidate);
        getReponseHeaders().addCacheControl(CacheControlTypes.max_age, Duration.standardMinutes(2));
        add(SurveyActeur.class);
    }

    @Override
    protected String getDescription() {
        return "Get a survey by id";
    }

    private static final class SurveyActeur extends Acteur {

        @Inject
        SurveyActeur(String value) throws JsonProcessingException {
            setState(new RespondWith(200, value));
        }
    }
}
