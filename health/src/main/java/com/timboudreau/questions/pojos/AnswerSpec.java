package com.timboudreau.questions.pojos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.timboudreau.questions.pojos.AnswerSpec.MultipleChoiceAnswer;
import com.timboudreau.questions.pojos.AnswerSpec.NumericAnswer;
import com.timboudreau.questions.pojos.AnswerSpec.TextAnswer;
import com.timboudreau.questions.pojos.AnswerSpec.TrueFalseAnswer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = TrueFalseAnswer.class, name = "truefalse"),
    @Type(value = NumericAnswer.class, name = "number"),
    @Type(value = TextAnswer.class, name = "text"),
    @Type(value = MultipleChoiceAnswer.class, name = "multiplechoice")
})
public abstract class AnswerSpec<T> implements Iterable<Constraint<T>> {

    public final String id;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public final List<Constraint<T>> constraints;

    @SuppressWarnings("unchecked")
    public AnswerSpec(String id, Constraint<?>... constraints) {
        this.id = id;
        // hack the generics - the ease of use of vararg Constraint... is worth it
        List l = Arrays.asList(constraints);
        this.constraints = l;
    }

    @JsonIgnore
    public abstract boolean isType(Object answerType);

    @Override
    public final Iterator<Constraint<T>> iterator() {
        return constraints == null ? new LinkedList<Constraint<T>>().iterator() : constraints.iterator();
    }

    @Type(name = "truefalse", value = TrueFalseAnswer.class)
    public static final class TrueFalseAnswer extends AnswerSpec<Boolean> {

        @JsonCreator
        public TrueFalseAnswer(@JsonProperty("id") String id, @JsonProperty("constraints") Constraint<?>... constraints) {
            super(id, constraints);
        }

        @Override
        @JsonIgnore
        public boolean isType(Object answerType) {
            return answerType != null && answerType instanceof Boolean;
        }
    }

    @Type(name = "multiplechoice", value = MultipleChoiceAnswer.class)
    public static final class MultipleChoiceAnswer extends AnswerSpec<String> {

        public final List<String> answers;

        @JsonCreator
        public MultipleChoiceAnswer(@JsonProperty("id") String id, @JsonProperty("answers") String... answers) {
            super(id);
            this.answers = answers == null ? new LinkedList<String>() : Arrays.asList(answers);
        }

        @Override
        public boolean isType(Object answerType) {
            return answerType != null && answerType instanceof String;
        }
    }

    @Type(name = "number", value = NumericAnswer.class)
    public static final class NumericAnswer extends AnswerSpec<Number> {

        @JsonCreator
        public NumericAnswer(@JsonProperty("id") String id, @JsonProperty(value = "constraints", required = false) Constraint<?>... constraints) {
            super(id, constraints);
        }

        @Override
        public boolean isType(Object answerType) {
            return answerType != null && answerType instanceof Number;
        }
    }

    @Type(name = "text", value = TextAnswer.class)
    public static final class TextAnswer extends AnswerSpec<String> {

        @JsonCreator
        public TextAnswer(@JsonProperty("id") String id, @JsonProperty(value = "constraints", required = false) Constraint<?>... constraints) {
            super(id, constraints);
        }

        @Override
        public boolean isType(Object answerType) {
            return answerType != null && answerType instanceof String;
        }
    }
}
