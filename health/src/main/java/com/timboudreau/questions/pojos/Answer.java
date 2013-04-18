package com.timboudreau.questions.pojos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * @author Tim Boudreau
 */
public final class Answer<T> {

    public final String id;
    public final T value;

    @JsonCreator
    public Answer(@JsonProperty(value = "id", required = true) String id,
            @JsonProperty(value = "value", required = true) T value) {
        this.id = id;
        this.value = value;
    }

    public <R> String validate(AnswerSpec<R> spec) {
        if (spec.isType(value)) {
            return validateSpec(spec, (R) value);
        }
        return "Wrong value type";
    }

    private <R> String validateSpec(AnswerSpec<R> spec, R value) {
        for (Constraint<R> c : spec.constraints) {
            if (!c.matches(value)) {
                return c.formatErrorMessage(value);
            }
        }
        return null;
    }
}
