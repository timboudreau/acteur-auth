package com.timboudreau.questions.pojos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;

/**
 *
 * @author Tim Boudreau
 */
//@JsonIgnoreProperties({"createdBy", "type"})
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Survey {

    public final ObjectId _id;
    public final String name;
    public final String description;
    public final Integer version;
    @JsonSerialize(typing = JsonSerialize.Typing.STATIC)
    public final List<Question> questions;
    public DateTime created;
    public DateTime lastModified;

    @JsonCreator
    public Survey(
            @JsonProperty(value = "_id", required = false) ObjectId id,
            @JsonProperty(value = "name", required = false) String name,
            @JsonProperty(value = "description", required = false) String description,
            @JsonProperty(value = "created", required = false) DateTime created,
            @JsonProperty(value = "lastModified", required = false) DateTime lastModified,
            @JsonProperty(value = "version", required = false) Integer version,
            @JsonProperty(value = "questions", required = true) List<Question> questions) {
        this._id = id;
        this.name = name;
        this.description = description;
        this.lastModified = lastModified;
        this.version = version;
        this.questions = questions;
    }
}
