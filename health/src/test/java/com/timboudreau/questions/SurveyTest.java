package com.timboudreau.questions;

import com.timboudreau.questions.pojos.Question;
import com.timboudreau.questions.pojos.AnswerSpec;
import com.timboudreau.questions.pojos.Survey;
import com.timboudreau.questions.pojos.Constraint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timboudreau.trackerapi.JacksonC;
import java.io.IOException;
import java.util.Arrays;
import org.joda.time.DateTime;
import org.junit.Test;
import static org.junit.Assert.*;

public class SurveyTest {

    @Test
    public void testSomeMethod() throws JsonProcessingException, IOException {
        assertTrue(true);
        ObjectMapper m = new JacksonC().configure(new ObjectMapper());
//        m.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);

        Question a = new Question("How is your arthritis?", "Help", new AnswerSpec.MultipleChoiceAnswer(
                "identifier", "Bad", "Good", "Purple"));

        Question b = new Question("Do you have hands", "Help help", new AnswerSpec.TrueFalseAnswer("hands", new Constraint.Required<>()));

        Question c = new Question("How old are you?", "Age help", new AnswerSpec.NumericAnswer("age", new Constraint.NonNegative(), new Constraint.AllowedRange(5, 120)));

        Question d = new Question("How do you feel?", "Feelings, nothing more than feelings", new AnswerSpec.TextAnswer("feel", new Constraint.ForbiddenCharacters("/;\\"), new Constraint.MaxLength(20)));

        Survey s = new Survey(null, "thingy", "How is your arthritis", new DateTime(), new DateTime(), 3, Arrays.asList(a, b, c, d));

        String val = m.writeValueAsString(s);

        System.out.println(val);

        Survey s1 = m.readValue(val, Survey.class);

    }

}
