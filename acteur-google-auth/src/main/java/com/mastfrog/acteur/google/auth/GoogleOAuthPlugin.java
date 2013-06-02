package com.mastfrog.acteur.google.auth;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.auth.OAuthPlugin;
import com.mastfrog.acteur.auth.OAuthPlugins;
import com.mastfrog.acteur.auth.UserFactory;
import static com.mastfrog.acteur.google.auth.GoogleOAuthModule.SETTINGS_KEY_ADDITIONAL_SCOPES;
import static com.mastfrog.acteur.google.auth.GoogleOAuthModule.SETTINGS_KEY_GOOGLE_CLIENT_ID;
import static com.mastfrog.acteur.google.auth.GoogleOAuthModule.SETTINGS_KEY_GOOGLE_CLIENT_SECRET;
import static com.mastfrog.acteur.google.auth.GoogleOAuthModule.SETTINGS_KEY_SCOPES;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.Path;
import com.mastfrog.url.URL;
import com.mastfrog.util.Checks;
import com.mastfrog.util.ConfigurationError;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.Strings;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OAuthPlugin for authenticating with Google.
 *
 * @author tim
 */
final class GoogleOAuthPlugin extends OAuthPlugin<GoogleCredential> {

    private final String clientId;
    private final String clientSecret;
    private final PathFactory paths;
    final HttpTransport transport;
    final JacksonFactory factory;
    private static final List<String> SCOPES = Arrays.asList("https://www.googleapis.com/auth/urlshortener",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile");
    private final Set<String> scopes = new HashSet<>();
    private final ObjectMapper mapper;

    @Inject
    public GoogleOAuthPlugin(@Named(SETTINGS_KEY_GOOGLE_CLIENT_ID) String clientId,
            @Named(SETTINGS_KEY_GOOGLE_CLIENT_SECRET) String clientSecret,
            PathFactory paths,
            Settings settings, OAuthPlugins plugins, ObjectMapper mapper, UserFactory<?> users,
            JacksonFactory factory, HttpTransport transport) {
        super("Google", "gg", "/g-small.png", plugins);
//        super("Google", "gg", "http://productforums.google.com/forum/google.png", plugins);

        if (!splitAndAdd(settings.getString(SETTINGS_KEY_SCOPES), scopes)) {
            scopes.addAll(SCOPES);
        }
        splitAndAdd(settings.getString(SETTINGS_KEY_ADDITIONAL_SCOPES), scopes);

        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.paths = paths;
        this.mapper = mapper;
        this.factory = factory;
        this.transport = transport;
    }

    private static boolean splitAndAdd(String commaDelimitedUrls, Set<? super String> set) {
        if (commaDelimitedUrls == null) {
            return false;
        }
        for (String s : commaDelimitedUrls.split(",")) {
            s = s.trim();
            if (s.isEmpty()) {
                continue;
            }
            URL u = URL.parse(s);
            if (!u.isValid()) {
                throw new ConfigurationError("Bad google scope url: " + u + " - " + u.getProblems());
            }
            set.add(s);
        }
        return true;
    }

    protected String getUserPictureURL(Map<String,Object> data) {
        return (String) data.get("picture");
    }    
    
    @Override
    public String getRedirectURL(UserFactory.LoginState state) {
        URL callbackUrl = paths.constructURL(Path.parse(plugins.getLandingPageBasePath()).append(code()), true);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(transport,
                factory, clientId, clientSecret,
                scopes)
                .setAccessType("offline")
                //                .setCredentialStore(store)
                .build();

        GoogleAuthorizationCodeRequestUrl url = flow.newAuthorizationUrl()
                .setRedirectUri(callbackUrl.toString()).setState(state.state);

        String u = url.build();

        return u;
    }

    private URI getRedirectURI() throws MalformedURLException, URISyntaxException {
        String pathToLandingPage = Strings.join(plugins.getLandingPageBasePath(), this.code());
        URL callbackUrl = paths.constructURL(Path.parse(pathToLandingPage), true);
        return callbackUrl.toJavaURL().toURI();
    }

    @Override
    public boolean revalidateCredential(String userName, String code) {
        return true;
    }

    public GoogleCredential getCredentialForCode(String code) throws IOException, MalformedURLException, URISyntaxException {
        Checks.notNull("code", code);
        URL callbackUrl = paths.constructURL(Path.parse(plugins.getLandingPageBasePath()).append(code()), true);
        GoogleTokenResponse response =
                new GoogleAuthorizationCodeTokenRequest(transport,
                factory, clientId, clientSecret,
                code,
                callbackUrl.toString()).execute();

        GoogleCredential cred = new GoogleCredential.Builder()
                .setTransport(transport)
                .setJsonFactory(factory)
                .setClientSecrets(clientId, clientSecret).build();
        
        return cred.setFromTokenResponse(response);
    }

    @Override
    public String stateForEvent(Event evt) {
        return evt.getParameter("state");
    }

    @Override
    protected String credentialToString(GoogleCredential credential) {
        return credential.getAccessToken();
    }

    @Override
    public GoogleCredential credentialForEvent(Event evt) {
        String code = evt.getParameter("code");
        if (code == null) {
            return null;
        }
        try {
            return getCredentialForCode(code);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        } catch (URISyntaxException ex) {
            Exceptions.printStackTrace(ex);
        }
        return null;
    }

    @Override
    public RemoteUserInfo getRemoteUserInfo(GoogleCredential credential) throws JsonParseException, IOException {
        GenericUrl url = new GenericUrl("https://www.googleapis.com/oauth2/v1/userinfo?alt=json");
        HttpRequest req = transport.createRequestFactory(credential).buildGetRequest(url);
        HttpResponse rs = req.execute();
        RUI rui = mapper.readValue(rs.getContent(), RUI.class);
        return rui;
    }

    public static class RUI extends HashMap<String, Object> implements RemoteUserInfo {

        public String userName() {
            return (String) get("email");
        }

        public String displayName() {
            return (String) get("name");
        }

        public Object get(String key) {
            return super.get(key);
        }
    }
}
