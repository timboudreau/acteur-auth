/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.acteur.mongo.userstore;

import com.google.common.base.Optional;
import com.mastfrog.acteur.auth.User;
import com.mastfrog.util.time.TimeUtil;
import com.mongodb.DBObject;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
public final class TTUser implements User<ObjectId> {

    final DBObject obj;
    public static final String HASHED_PASSWORD = "pass";
    public static final String DISPLAY_NAME = "displayName";
    public static final String AUTHORIZES = "authorizes";
    public static final String VERSION = "version";
    public static final String NAME = "name";
    public static final String ID = "_id";
    public static final String AUTH_INFO = "auths";
    public static final String SLUG = "slug";
    public static final String CREATED = "created";
    public static final String LAST_MODIFIED = "lastModified";
    public static final String TOKEN = "token";

    TTUser(DBObject obj) {
        this.obj = obj;
    }

    @SuppressWarnings("unchecked")
    public boolean authorizes(ObjectId id) {
        List<ObjectId> l = (List<ObjectId>) obj.get(AUTHORIZES);
        return l == null ? false : l.contains(id);
    }

    public int version() {
        Number n = (Number) obj.get(VERSION);
        return n == null ? 0 : n.intValue();
    }

    public String idAsString() {
        ObjectId id = (ObjectId) obj.get("_id");
        return "" + id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> names() {
        List<String> l = (List<String>) obj.get(NAME);
        return l == null ? Collections.<String>emptyList() : l;
    }

    @Override
    public String name() {
        List<String> l = names();
        return l.isEmpty() ? "[no name]" : l.get(0);
    }

    @Override
    public ObjectId id() {
        return (ObjectId) obj.get(ID);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ObjectId> authorizes() {
        List<ObjectId> authorizes = (List<ObjectId>) obj.get(AUTHORIZES);
        return authorizes == null ? Collections.<ObjectId>emptyList() : authorizes;
    }

    @Override
    public String displayName() {
        String result = (String) obj.get(DISPLAY_NAME);
        return result == null ? name() : result;
    }

    @Override
    public String hashedPassword() {
        return (String) obj.get(HASHED_PASSWORD);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> authInfoNames() {
        Map<String, Object> m = (Map<String, Object>) obj.get(AUTH_INFO);
        return m == null ? Collections.<String>emptySet() : m.keySet();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<OAuthInfo> authInfo(String serviceCode) {
        Map<String, Object> m = (Map<String, Object>) obj.get(AUTH_INFO);
        DBObject ob = (DBObject) m.get(serviceCode);
        return ob == null ? Optional.<OAuthInfo>absent() : Optional.<OAuthInfo>of(new OAuthInfoImpl(serviceCode, ob));
    }

    private static class OAuthInfoImpl implements OAuthInfo {

        private final String serviceCode;
        private final DBObject obj;

        public OAuthInfoImpl(String serviceCode, DBObject obj) {
            this.serviceCode = serviceCode;
            this.obj = obj;
        }

        @Override
        public String slug() {
            return (String) obj.get(SLUG);
        }

        @Override
        public ZonedDateTime lastModified() {
            Object o = obj.get(LAST_MODIFIED);
            if (o instanceof Date) {
//                return new DateTime((Date) o);
                return ((Date) o).toInstant().atZone(ZoneId.systemDefault());
            } else if (o instanceof Number) {
                return TimeUtil.fromUnixTimestamp(((Number) o).longValue());
            } else {
                return ZonedDateTime.now();
            }
        }

        @Override
        public Optional<String> savedToken() {
            return Optional.fromNullable((String) obj.get(TOKEN));
        }

        @Override
        public String service() {
            return serviceCode;
        }
    }
}
