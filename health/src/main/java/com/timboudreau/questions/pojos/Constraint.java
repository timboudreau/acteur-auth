package com.timboudreau.questions.pojos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.timboudreau.questions.pojos.Constraint.ForbiddenCharacters;
import com.timboudreau.questions.pojos.Constraint.NonNegative;
import com.timboudreau.questions.pojos.Constraint.AllowedRange;
import com.timboudreau.questions.pojos.Constraint.MaxLength;
import com.timboudreau.questions.pojos.Constraint.Required;
import java.util.Arrays;

/**
 *
 * @author Tim Boudreau
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NonNegative.class, name = "non-negative"),
    @JsonSubTypes.Type(value = Required.class, name = "required"),
    @JsonSubTypes.Type(value = AllowedRange.class, name = "allowed-range"),
    @JsonSubTypes.Type(value = ForbiddenCharacters.class, name = "forbidden-characters"),
    @JsonSubTypes.Type(value = MaxLength.class, name = "max-length")
})
public abstract class Constraint<T> {

    public abstract boolean matches(T val);
    
    public String formatErrorMessage(T val) {
        // Can override and localize or whatever
        return "Does not match constraint " + getClass().getSimpleName() + ": '" + val + "'";
    }

    public static class NonNegative extends Constraint<Number> {

        @Override
        public boolean matches(Number val) {
            return val.doubleValue() >= 0;
        }
    }

    public static class Required<T> extends Constraint<T> {

        @Override
        public boolean matches(T val) {
            return val != null;
        }
    }

    public static class AllowedRange extends Constraint<Number> {

        public final int min;
        public final int max;

        @JsonCreator
        public AllowedRange(@JsonProperty("min") int min, @JsonProperty("max") int max) {
            this.min = Math.min(min, max);
            this.max = Math.max(min, max);
        }

        @Override
        public boolean matches(Number val) {
            if (val != null) {
                return val.intValue() >= min && val.intValue() <= max;
            }
            return true;
        }
    }

    public static class MaxLength extends Constraint<String> {

        public final int length;

        @JsonCreator
        public MaxLength(@JsonProperty("length") int length) {
            this.length = length;
        }

        @Override
        public boolean matches(String val) {
            return val != null && val.length() <= length;
        }
    }

    public static class ForbiddenCharacters extends Constraint<String> {

        public final char[] chars;

        @JsonCreator
        public ForbiddenCharacters(@JsonProperty("chars") String chars) {
            char[] c = chars.toCharArray();
            Arrays.sort(c);
            this.chars = c;
        }

        @Override
        public boolean matches(String val) {
            if (val != null) {
                for (char c : val.toCharArray()) {
                    if (Arrays.binarySearch(chars, c) >= 0) {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}
