package com.timboudreau.questions;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.mastfrog.acteur.Event;
import com.mastfrog.util.Exceptions;
import com.timboudreau.questions.pojos.Survey;
import java.io.IOException;

/**
 *
 * @author Tim Boudreau
 */
final class SurveyProvider implements Provider<Survey> {

    private final Provider<Event> evt;

    @Inject
    SurveyProvider(Provider<Event> evt) {
        this.evt = evt;
    }

    @Override
    public Survey get() {
        try {
            Event e = evt.get();
            return e.getContentAsJSON(Survey.class);
        } catch (IOException ex) {
            return Exceptions.chuck(ex);
        }
    }
}
