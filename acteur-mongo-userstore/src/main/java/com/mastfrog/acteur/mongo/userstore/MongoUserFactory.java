package com.mastfrog.acteur.mongo.userstore;

import com.google.common.base.Optional;
import com.google.inject.name.Named;
import com.mastfrog.acteur.auth.UniqueIDs;
import com.mastfrog.acteur.auth.UserFactory;
import com.mastfrog.util.Checks;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 *
 * @author tim
 */
public class MongoUserFactory extends UserFactory<DBObject> {

    private final DBCollection users;
    public static final String USERS_COLLECTION_NAME = "users";
    public static final String LOGIN_STATE_COLLECTION_NAME = "login";
    private final DBCollection loginStates;
    private final UserObjectAdapter adap;

    @Inject
    public MongoUserFactory(UniqueIDs ids,
            @Named(USERS_COLLECTION_NAME) DBCollection users,
            @Named(LOGIN_STATE_COLLECTION_NAME) DBCollection loginStates,
            UserObjectAdapter adap) {
        super(DBObject.class, ids);
        this.users = users;
        this.loginStates = loginStates;
        this.adap = adap;
    }

    @Override
    public Optional<DBObject> findUserByName(String name) {
        Checks.notNull("name", name);
        BasicDBObject query = new BasicDBObject("name", name);
        DBObject user = users.findOne(query);
        return Optional.fromNullable(user);
    }

    @Override
    public Optional<String> getPasswordHash(DBObject user) {
        return Optional.fromNullable((String) user.get("pass"));
    }

    @Override
    public void setPasswordHash(DBObject on, String hash) {
        DBObject query = new BasicDBObject("_id", on.get("_id"));

        DBObject update = new BasicDBObject("$set", new BasicDBObject("pass", hash)).append("$inc",
                new BasicDBObject("version", 1));

        WriteResult res = users.update(query, update, false, false, WriteConcern.FSYNCED);
    }

    @Override
    public Object toUserObject(DBObject obj) {
        return new TTUser(obj);
    }

    @Override
    protected void putSlug(DBObject on, Slug slug) {
        DBObject query = new BasicDBObject("_id", on.get("_id"));

        DBObject slugObj = new BasicDBObject("slug", slug.slug)
                .append("created", slug.created.getMillis());
        DBObject update = new BasicDBObject("$set", new BasicDBObject("slugs." + slug.name, slugObj)).append("$inc",
                new BasicDBObject("version", 1));
        WriteResult res = users.update(query, update, false, false, WriteConcern.FSYNCED);
    }

    @Override
    protected Slug getSlug(DBObject on, String name) {
        DBObject slugs = (DBObject) on.get("slugs");
        if (slugs != null) {
            DBObject slugObj = (DBObject) slugs.get(name);
            if (slugObj != null) {
                Number n = (Number) slugObj.get("created");
                return new Slug(name, (String) slugObj.get("slug"), new DateTime(n.longValue()));
            }
        }
        return null;
    }

    @Override
    public Optional<String> getAccessToken(DBObject on, String serviceName) {
        DBObject tokens = (DBObject) on.get("tokens");
        if (tokens != null) {
            String result = (String) tokens.get(serviceName);
            return Optional.fromNullable(result);
        }
        return Optional.absent();
    }

    @Override
    public void putAccessToken(DBObject on, String token, String serviceName) {
        DBObject query = new BasicDBObject("_id", on.get("_id"));

        DBObject update = new BasicDBObject("$set", new BasicDBObject("tokens." + serviceName, token)).append("$inc",
                new BasicDBObject("version", 1));
        WriteResult res = users.update(query, update, false, false, WriteConcern.FSYNCED);
    }

    @Override
    public Set<String> getSlugNames(DBObject on) {
        DBObject slugs = (DBObject) on.get("slugs");
        return slugs == null ? Collections.<String>emptySet() : slugs.keySet();
    }

    @Override
    public DBObject newUser(String name, Slug slug, String displayName, Map<String, Object> properties) {
        DBObject query = new BasicDBObject("name", name);
        DBObject existing = users.findOne(query);
        if (existing != null) {
            return null;
        }
        DBObject slugData = new BasicDBObject("created", slug.created.getMillis()).append("slug", slug.slug);
        DBObject toWrite = new BasicDBObject("name", new String[] {name}).append("displayName", displayName).append("version", 0).append("slugs", new BasicDBObject(slug.name, slugData)).append("tokens", new BasicDBObject()).append("pass", ids.newRandomString());
        WriteResult res = users.insert(toWrite);
        return toWrite;
    }

    @Override
    public DBObject newUser(String name, String hashedPassword, String displayName, Map<String, Object> properties) {
        DBObject query = new BasicDBObject("name", name);
        DBObject existing = users.findOne(query);
        if (existing != null) {
            System.out.println("User already exists: " + existing);
            return null;
        }
        DBObject toWrite = new BasicDBObject("name", new String[] {name}).append("displayName", displayName).append("version", 0).append("slugs", new BasicDBObject()).append("tokens", new BasicDBObject()).append("pass", hashedPassword);
        WriteResult res = users.insert(toWrite);
        return toWrite;
    }

    @Override
    protected void saveLoginState(LoginState state) {
        DBObject writeTo = new BasicDBObject("state", state.state)
                .append("created", state.created.getMillis())
                .append("redir", state.redirectTo);
        WriteResult res = loginStates.insert(writeTo);
    }

    @Override
    public Optional<LoginState> lookupLoginState(String state) {
        Checks.notNull("state", state);
        DBObject query = new BasicDBObject("state", state);
        DBObject result = loginStates.findOne(query);
        if (result != null) {
            Number n = (Number) result.get("created");
            DateTime created = new DateTime(n.longValue());
            String redir = (String) result.get("redir");
            return Optional.of(new LoginState(state, redir, created));
        }
        return Optional.absent();
    }

}
