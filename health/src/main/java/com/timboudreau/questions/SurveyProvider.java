package com.timboudreau.questions;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.mastfrog.acteur.ContentConverter;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.util.Exceptions;
import com.timboudreau.questions.pojos.Survey;

/**
 *
 * @author Tim Boudreau
 */
final class SurveyProvider implements Provider<Survey> {

    private final Provider<HttpEvent> evt;
    private final Provider<ContentConverter> cvt;

    @Inject
    SurveyProvider(Provider<HttpEvent> evt, Provider<ContentConverter> cvt) {
        this.evt = evt;
        this.cvt = cvt;
    }

    @Override
    public Survey get() {
        try {
            HttpEvent e = evt.get();
            ContentConverter c = cvt.get();
            return c.toObject(e.content(), e.header(Headers.CONTENT_TYPE), Survey.class);
        } catch (Exception ex) {
            return Exceptions.chuck(ex);
        }
    }
}
