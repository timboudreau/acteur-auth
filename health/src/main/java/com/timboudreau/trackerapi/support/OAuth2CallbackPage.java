package com.timboudreau.trackerapi.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.util.Method;
import com.mastfrog.acteur.util.PasswordHasher;
import com.mastfrog.guicy.annotations.Defaults;
import com.mastfrog.guicy.annotations.Namespace;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.timboudreau.trackerapi.Properties;
import com.timboudreau.trackerapi.Timetracker;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.ServerCookieEncoder;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Map;
import org.joda.time.DateTime;

/**
 *
 * @author tim
 */
@Namespace(Timetracker.TIMETRACKER)
@Defaults("loginUrl=/users/${user}/")
public class OAuth2CallbackPage extends Page {

    @Inject
    OAuth2CallbackPage(ActeurFactory af) {
        add(af.matchMethods(Method.GET, Method.PUT, Method.POST));
        add(af.matchPath("^oauth2callback$"));
        add(af.requireParameters("code", "state"));
        add(CallbackHandler.class);
    }

    private static final class CallbackHandler extends Acteur {

        @Inject
        CallbackHandler(Event evt, @Named("auth") DBCollection auths, GoogleAuth ga, @Named("users") DBCollection users, ObjectMapper mapper, PasswordHasher hasher, AuthSupport supp, @Named("loginUrl") String loginUrl) throws MalformedURLException, URISyntaxException, IOException {
            String code = evt.getParameter("code");
            String state = evt.getParameter("state");
            System.out.println("GOOGLE AUTH CALLBACK " + code + " - " + state);
            DBObject ob = auths.findOne(new BasicDBObject("uid", state));
            if (ob == null) {
                setState(new RespondWith(400, "No such uid " + state));
                return;
            }
            GoogleCredential credential = null;

            try {
                credential = ga.getCredentialForCode(code);
            } catch (TokenResponseException tre) {
                tre.printStackTrace();
                add(Headers.LOCATION, new URI("./google"));
                setState(new RespondWith(HttpResponseStatus.SEE_OTHER, tre.toString()));
                return;
            }

            BasicDBObject dbo = new BasicDBObject("accessToken", credential.getAccessToken());
            dbo.append("expiresSeconds", credential.getExpiresInSeconds());
            dbo.append("refreshToken", credential.getRefreshToken());

            // https://www.googleapis.com/userinfo/email
            // https://www.googleapis.com/oauth2/v1/userinfo
            GenericUrl url = new GenericUrl("https://www.googleapis.com/oauth2/v1/userinfo?alt=json");
            HttpRequest req = ga.transport.createRequestFactory(credential).buildGetRequest(url);

            /*
             {
             "id": "117082367181440440444",
             "email": "niftiness@gmail.com",
             "verified_email": true,
             "name": "Tim Boudreau",
             "given_name": "Tim",
             "family_name": "Boudreau",
             "link": "https://plus.google.com/117082367181440440444",
             "picture": "https://lh5.googleusercontent.com/-U700W5-I4dw/AAAAAAAAAAI/AAAAAAAAAEU/yYC6Nn-zWtQ/photo.jpg",
             "gender": "male",
             "birthday": "0000-01-18",
             "locale": "en"
             }
             */
//            HttpRequest req = ga.transport.createRequestFactory(c).buildGetRequest(new GenericUrl("https://www.googleapis.com/userinfo/email?alt=json"));
            HttpResponse rs = req.execute();
            Map<String, String> m = mapper.readValue(rs.getContent(), Map.class);

            String email = m.get("email");
            BasicDBObject query = new BasicDBObject("name", Arrays.asList(email));
            System.out.println("Search for user with email " + email);
            DBObject user = users.findOne(query);
            if (user == null) {
                query.append("displayName", m.get("name"));
                if (m.containsKey("picture")) {
                    query.append("picture", m.get("picture"));
                }
                query.append("googleInfo", m);
                query.append("googletoken", credential.getAccessToken());
                query.append("version", 0);
                query.append("created", DateTime.now().getMillis());
                // Just put *something* in the password field
                String hashedPass = hasher.encryptPassword(credential.getAccessToken());
                query.append(Properties.pass, hashedPass);

                users.insert(query, WriteConcern.FSYNCED);
                user = query;
            }

            Cookie cookie = supp.encodeLoginCookie(user, m.get("email"), (String) user.get(Properties.pass));
            add(Headers.SET_COOKIE, cookie);
            Cookie dn = supp.encodeDisplayNameCookie(m.get("name"));
            add(Headers.SET_COOKIE, dn);
            System.out.println("ADD USER " + user);

            String name = m.get("email");
            name = URLEncoder.encode(name, "UTF-8");
            URI loc = new URI(loginUrl.replace("${user}", name));
            add(Headers.LOCATION, loc);

            setState(new RespondWith(HttpResponseStatus.FOUND, user.toMap()));
        }
    }
}
