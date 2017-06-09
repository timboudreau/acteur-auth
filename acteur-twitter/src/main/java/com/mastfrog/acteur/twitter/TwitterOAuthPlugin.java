package com.mastfrog.acteur.twitter;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.auth.OAuthPlugin;
import com.mastfrog.acteur.auth.OAuthPlugins;
import com.mastfrog.acteur.auth.UniqueIDs;
import com.mastfrog.acteur.auth.UserFactory;
import com.mastfrog.acteur.server.PathFactory;
import static com.mastfrog.acteur.twitter.TwitterOAuthModule.SETTINGS_KEY_CONSUMER_SECRET;
import static com.mastfrog.acteur.twitter.TwitterOAuthModule.SETTINGS_KEY_CONSUMER_key;
import com.mastfrog.acteur.twitter.TwitterOAuthPlugin.TwitterToken;
import com.mastfrog.netty.http.client.HttpClient;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.Path;
import com.mastfrog.util.Exceptions;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
public class TwitterOAuthPlugin extends OAuthPlugin<TwitterToken> {

    private static final String REQUEST_TOKEN_URL = "https://api.twitter.com/oauth/request_token";
    private static final String AUTHORIZE_URL = "https://api.twitter.com/oauth/authorize";
    private static final String ACCESS_TOKEN_URL = "https://api.twitter.com/oauth/access_token";
    private final String consumerKey;
    private final String consumerSecret;
    private final PathFactory paths;
    private final UserFactory<?> users;
    private final HttpClient client;
    private final UniqueIDs ids;

    @Inject
    TwitterOAuthPlugin(@Named(SETTINGS_KEY_CONSUMER_SECRET) String consumerSecret,
            @Named(SETTINGS_KEY_CONSUMER_key) String consumerKey,
            PathFactory paths, UniqueIDs ids,
            Settings settings, OAuthPlugins plugins, ObjectMapper mapper, UserFactory<?> users,
            HttpClient client) {
        super("Twitter", "tw", "/twitter-official.png", plugins);
        this.consumerSecret = consumerSecret;
        this.consumerKey = consumerKey;
        this.ids = ids;
        this.paths = paths;
        this.users = users;
        this.client = client;
    }

    @Override
    public String stateForEvent(HttpEvent evt) {
        return "_nastyHack";
    }

    @Override
    public String getRedirectURL(UserFactory.LoginState state) {
        try {
            OAuthResult result = start(state.state);
            return "https://twitter.com/oauth/authenticate?oauth_token=" + result.token;
        } catch (IOException | InterruptedException | GeneralSecurityException ex) {
            return Exceptions.chuck(ex);
        }
    }

    private OAuthResult start(String state) throws IOException, InterruptedException, GeneralSecurityException {
        Path pth = Path.builder().add(plugins.getLandingPageBasePath()).add(code()).create();
        com.mastfrog.url.URL url = paths.constructURL(pth, true);
        TwitterSign signer = new TwitterSign(consumerKey, consumerSecret, url.toString(), client);
        return signer.startTwitterAuthentication(state);
    }

    @Override
    public boolean revalidateCredential(String userName, String accessToken) {
        return true;
    }

    @Override
    public TwitterToken credentialForEvent(HttpEvent evt) {
        String token = evt.urlParameter("oauth_token");
        String verifier = evt.urlParameter("oauth_verifier");
        return new TwitterToken(token, verifier);
    }

    public static final class TwitterToken {

        final String token;
        final String verifier;

        public TwitterToken(String token, String verifier) {
            this.token = token;
            this.verifier = verifier;
        }

    }

    @Override
    protected String getUserPictureURL(Map<String, Object> data) {
        String ur = (String) data.get("picture");
        if (ur == null) {
            String screenName = (String) data.get("screen_name");
            if (screenName != null) {
                ur = "https://api.twitter.com/1/users/profile_image?screen_name=" + screenName + "&size=bigger";
            }
        }
        return ur;
    }

    @Override
    protected String credentialToString(TwitterToken credential) {
        return credential.token;
    }

    @Override
    public RemoteUserInfo getRemoteUserInfo(TwitterToken credential) throws IOException, JsonParseException, JsonMappingException {
        TwitterSign sg = new TwitterSign(consumerKey, consumerSecret, null, client);
        try {
            AuthorizationResponse auth = sg.getTwitterAccessTokenFromAuthorizationCode(credential.verifier, credential.token, ids.newId());

            if (auth == null) {
                return null;
            }
            String nonce = ids.newId();
            // Sign with a new nonce for *every* request - really?!
            OAuthResult r = start(nonce);

            sg = new TwitterSign(consumerKey, consumerSecret, null, client);

            //PENDING - need to store the access token
//            users.putAccessToken(sg, code, name);
            return sg.getUserInfo(ids.newId(), credential, auth);
        } catch (InterruptedException | GeneralSecurityException ex) {
            return Exceptions.chuck(ex);
        }
    }
}
