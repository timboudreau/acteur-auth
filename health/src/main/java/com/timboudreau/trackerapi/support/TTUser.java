package com.timboudreau.trackerapi.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
public final class TTUser {

    public final String name;
    public final ObjectId id;
    private final int version;
    private final List<ObjectId> authorizes;

    public TTUser(String name, ObjectId id, int version, List<ObjectId> authorizes) {
        this.name = name;
        this.id = id;
        this.version = version;
        this.authorizes = authorizes == null ? Collections.<ObjectId>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(authorizes));
    }

    boolean authorizes(ObjectId id) {
        return authorizes != null && authorizes.contains(id);
    }

    public int version() {
        return version;
    }

    public StringBuilder toVariableName() {
        char[] c = name.toCharArray();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.length; i++) {
            if (!Character.isWhitespace(c[i])) {
                sb.append(c[i]);
            }
        }
        return sb;
    }

    public String idAsString() {
        return id.toStringMongod();
    }
}
