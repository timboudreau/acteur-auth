package com.timboudreau.trackerapi.support;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.CredentialStore;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.url.Path;
import com.mastfrog.url.URL;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import org.joda.time.DateTime;

/**
 *
 * @author tim
 */
@Singleton
public class GoogleAuth {

    private static final List<String> SCOPES = Arrays.asList("https://www.googleapis.com/auth/urlshortener",
            "https://www.googleapis.com/auth/userinfo.email", "https://www.googleapis.com/auth/userinfo.profile");
    private final String clientId;
    private final String appsKey;
    private final String clientSecret;
    private final PathFactory paths;
    private final DBCollection auths;
    private final MongoCredentialStore store;
    private final UniqueIDs ids;
    public final NetHttpTransport transport = new NetHttpTransport();
    public final JacksonFactory factory = new JacksonFactory();

    @Inject
    GoogleAuth(@Named("google.client.id") String clientId, @Named("google.server.key") String appsKey,
            @Named("google.client.secret") String clientSecret, PathFactory paths,
            @Named("auth") DBCollection auths,
            MongoCredentialStore store, UniqueIDs ids) {
        this.clientId = clientId;
        this.appsKey = appsKey;
        this.clientSecret = clientSecret;
        this.paths = paths;
        this.auths = auths;
        this.store = store;
        this.ids = ids;
    }

    private URI getRedirectURI() throws MalformedURLException, URISyntaxException {
        URL callbackUrl = paths.constructURL(Path.builder().add("oauth2callback").create(), true);
        return callbackUrl.toJavaURL().toURI();
    }

    public String getRedirectURL(String referrer) {
        URL callbackUrl = paths.constructURL(Path.builder().add("oauth2callback").create(), true);

        System.out.println("Callback url is " + callbackUrl);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(transport,
                factory, clientId, clientSecret,
                SCOPES)
                .setAccessType("offline").setCredentialStore(store).build();

        String uid = ids.newId();
        System.out.println("Created id " + uid);

        GoogleAuthorizationCodeRequestUrl url = flow.newAuthorizationUrl()
                .setRedirectUri(callbackUrl.toString()).setState(uid);

        BasicDBObject ob = new BasicDBObject("uid", uid).append("referrer", referrer).append("created", DateTime.now().toDate());
        WriteResult res = auths.insert(ob, WriteConcern.FSYNCED);
        if (res.getN() < 1) {
            System.out.println("NO WRITE?! " + ob);
        }
        System.out.println("WRITE TO DB " + ob);

        String u = url.build();

        System.out.println("URL: " + u);
        return u;
    }

    public GoogleCredential getCredentialForCode(String code) throws MalformedURLException, URISyntaxException, IOException {
        GoogleTokenResponse response =
                new GoogleAuthorizationCodeTokenRequest(transport,
                factory, clientId, clientSecret,
                code,
                getRedirectURI().toString()).execute();
        
        GoogleCredential cred =new GoogleCredential.Builder().setTransport(transport).setJsonFactory(factory).setClientSecrets(clientId, clientSecret).build();
        
        return cred.setFromTokenResponse(response);
    }

    static class MongoCredentialStore implements CredentialStore {

        private final DBCollection users;

        @Inject
        MongoCredentialStore(@Named("users") DBCollection users) {
            this.users = users;
        }

        @Override
        public boolean load(String string, Credential crdntl) throws IOException {
            System.out.println("LOAD " + string + " " + crdntl);
            DBObject ob = users.findOne(new BasicDBObject("name", string));
            if (ob == null) {
                return false;
            }
            String token = (String) ob.get("googletoken");
            System.out.println("LOAD " + string + " token " + token);
            
            if (token == null) {
                return false;
            }
            crdntl.setAccessToken(token);
            return true;
        }

        @Override
        public void store(String string, Credential crdntl) throws IOException {
            System.out.println("STORE " + string + " " + crdntl);
            String token = crdntl.getAccessToken();
            assert token != null;
            DBObject query = new BasicDBObject("name", string);

            BasicDBObject edit = new BasicDBObject();
            BasicDBObject set = new BasicDBObject();
            edit.append("$set", set);
            set.put("googletoken", token);
            BasicDBObject inc = new BasicDBObject("version", 1);
            edit.append("$inc", inc);
            WriteResult res = users.update(query, edit);
            System.out.println("Stored token " + string + " - updates " + res.getN());
        }

        @Override
        public void delete(String string, Credential crdntl) throws IOException {
            System.out.println("DELETE " + string + " " + crdntl);
            String token = crdntl.getAccessToken();
            assert token != null;
            DBObject query = new BasicDBObject("name", string);

            BasicDBObject edit = new BasicDBObject();
            BasicDBObject unset = new BasicDBObject();
            edit.append("$unset", unset);
            unset.put("googletoken", 1);
            BasicDBObject inc = new BasicDBObject("version", 1);
            edit.append("$inc", inc);
            WriteResult res = users.update(query, edit);
        }
    }
}
