package com.mastfrog.acteur.facebook.auth;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.auth.OAuthPlugin;
import com.mastfrog.acteur.auth.OAuthPlugins;
import com.mastfrog.acteur.auth.UserFactory;
import static com.mastfrog.acteur.facebook.auth.FacebookOAuthModule.SETTINGS_KEY_FACEBOOK_APP_ID;
import static com.mastfrog.acteur.facebook.auth.FacebookOAuthModule.SETTINGS_KEY_FACEBOOK_APP_SECRET;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.Path;
import com.mastfrog.url.URL;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.FacebookApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

/**
 * OAuthPlugin for authenticating with Facebook.
 *
 * @author tim
 */
final class FacebookOAuthPlugin extends OAuthPlugin<Token> {

    private final String appId;
    private final String appSecret;
    private final PathFactory paths;
    private final ObjectMapper mapper;
    private final OAuthService oauthService;

    @Inject
    public FacebookOAuthPlugin(@Named(SETTINGS_KEY_FACEBOOK_APP_ID) String apiKey,
            @Named(SETTINGS_KEY_FACEBOOK_APP_SECRET) String appSecret,
            PathFactory paths,
            Settings settings, OAuthPlugins plugins, ObjectMapper mapper, UserFactory<?> users) {
        super("Facebook", "fb", "https://fbstatic-a.akamaihd.net/rsrc.php/v2/yr/r/KHAfgp45Qko.png", plugins);
        this.appId = apiKey;
        this.appSecret = appSecret;
        this.paths = paths;
        this.mapper = mapper;

        Path pth = Path.parse(plugins.getLandingPageBasePath()).append(code);
        URL fullLandingPageURL = paths.constructURL(pth, true);

        oauthService = new ServiceBuilder()
                .provider(FacebookApi.class)
                .apiKey(apiKey)
                .apiSecret(appSecret)
                .callback(fullLandingPageURL.toString())
                .build();

//        FacebookClient client = new DefaultFacebookClient(apiKey);
    }

    @Override
    public String getRedirectURL(UserFactory.LoginState state) {
        URL callbackUrl = paths.constructURL(Path.parse(plugins.getLandingPageBasePath()).append(code()), true);
        callbackUrl = callbackUrl.withParameter("state", state.state);

        String redirUrl = oauthService.getAuthorizationUrl(Token.empty());
        if (redirUrl.contains("?")) {
            redirUrl += "&state=" + state.state;
        } else {
            redirUrl += "?state=" + state.state;
        }
        return redirUrl;
    }

    @Override
    public boolean revalidateCredential(String userName, String code) {
        return true;
    }

    public Token getCredentialForCode(String code) {
        URL callbackUrl = paths.constructURL(Path.parse(plugins.getLandingPageBasePath()).append(code()), true);
        Verifier verifier = new Verifier(code);
        Token accessToken = oauthService.getAccessToken(Token.empty(), verifier);
        return accessToken;
    }

    @Override
    public String stateForEvent(HttpEvent evt) {
        return evt.urlParameter("state");
    }

    @Override
    public Token credentialForEvent(HttpEvent evt) {
        String code = evt.urlParameter("code");
//        return new Token(code, this.appSecret);
        return getCredentialForCode(code);
    }

    @Override
    public RemoteUserInfo getRemoteUserInfo(Token credential) throws JsonParseException, IOException {
        OAuthRequest req = new OAuthRequest(Verb.GET, "https://graph.facebook.com/me");
        oauthService.signRequest(credential, req);
        Response apiResponse = req.send();
        // XXX follow with getting the user picture url:
        // https://graph.facebook.com/[fb_user_id]?fields=picture.type(small)
        if (apiResponse.isSuccessful()) {
            RUI rui = mapper.readValue(apiResponse.getBody(), RUI.class);
            return rui;
        } else {
            System.out.println("PROBLEM: " + apiResponse.getCode() + " - " + apiResponse.getHeaders());
            System.out.println(apiResponse.getBody());
        }
        return null;
    }

    @Override
    protected String credentialToString(Token credential) {
        return credential.getToken();
    }
    
    protected String getUserPictureURL(Map<String,Object> data) {
        String un = (String) data.get("username");
        if (un != null) {
            return "https://graph.facebook.com/" + un + "/picture";
        }
        return null;
    }

    public static class RUI extends HashMap<String, Object> implements RemoteUserInfo {

        public String userName() {
            return (String) get("username") + "@facebook.com";
        }

        public String displayName() {
            return (String) get("name");
        }

        public Object get(String key) {
            return super.get(key);
        }
    }
}
