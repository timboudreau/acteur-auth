package com.timboudreau.questions.pojos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * @author Tim Boudreau
 */
public class Question {

    public final String description;
    public final String help;
    public final AnswerSpec answerType;

    @JsonCreator
    public Question(@JsonProperty(value = "description", required = true) String description,
            @JsonProperty(value = "help", required = false) String help,
            @JsonProperty(value = "type", required = true) AnswerSpec answerType) {
        this.description = description;
        this.help = help;
        this.answerType = answerType;
    }
}
