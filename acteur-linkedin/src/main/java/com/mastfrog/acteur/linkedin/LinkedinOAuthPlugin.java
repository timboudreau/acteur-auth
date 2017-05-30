package com.mastfrog.acteur.linkedin;

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
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import static com.mastfrog.acteur.linkedin.LinkedinOAuthModule.SETTINGS_KEY_API_KEY;
import static com.mastfrog.acteur.linkedin.LinkedinOAuthModule.SETTINGS_KEY_SECRET_KEY;
import com.mastfrog.acteur.linkedin.LinkedinOAuthPlugin.LinkedinToken;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.netty.http.client.HttpClient;
import com.mastfrog.netty.http.client.ResponseHandler;
import com.mastfrog.netty.http.client.State;
import com.mastfrog.netty.http.client.StateType;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.Path;
import com.mastfrog.url.URL;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.thread.Receiver;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Tim Boudreau
 */
public class LinkedinOAuthPlugin extends OAuthPlugin<LinkedinToken> {

    private static final String AUTHORIZE_URL = "https://www.linkedin.com/uas/oauth2/authorization";
    private static final String ACCESS_TOKEN_URL = "https://www.linkedin.com/uas/oauth2/accessToken";

    private final String consumerKey;
    private final String consumerSecret;
    private final PathFactory paths;
    private final UserFactory<?> users;
    private final HttpClient client;
    private final UniqueIDs ids;

    @Inject
    LinkedinOAuthPlugin(@Named(SETTINGS_KEY_SECRET_KEY) String consumerSecret,
            @Named(SETTINGS_KEY_API_KEY) String consumerKey,
            PathFactory paths, UniqueIDs ids,
            Settings settings, OAuthPlugins plugins, ObjectMapper mapper, UserFactory<?> users,
            HttpClient client) {
        super("LinkedIn", "li", "/linkedin-small.png", plugins);
        this.consumerSecret = consumerSecret;
        this.consumerKey = consumerKey;
        this.ids = ids;
        this.paths = paths;
        this.users = users;
        this.client = client;
    }

    @Override
    public String stateForEvent(HttpEvent evt) {
        return evt.getParameter("state");
    }

    @Override
    public String getRedirectURL(UserFactory.LoginState state) {
        return getRedirectURL(state.state);
    }

    public String getRedirectURL(String state) {
        URL callbackUrl = paths.constructURL(Path.parse(plugins.getLandingPageBasePath()).append(code()), true);
        return AUTHORIZE_URL + "?responseType=code&client_id=" + consumerKey + "&state="
                + state + "&redirect_uri=" + callbackUrl;
    }

    @Override
    public boolean revalidateCredential(String userName, String accessToken) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public LinkedinToken credentialForEvent(HttpEvent evt) {
        System.out.println("EVENT IS " + evt.getPath());
        System.out.println("Credential for event " + evt.getParametersAsMap());

        String token = evt.getParameter("code");
        String state = evt.getParameter("state");
        return new LinkedinToken(token, state);
    }

    public static final class LinkedinToken {

        final String token;
        final String state;

        public LinkedinToken(String token, String state) {
            this.token = token;
            this.state = state;
        }
    }

    @Override
    protected String getUserPictureURL(Map<String, Object> data) {
        String ur = (String) data.get("picture");
        if (ur == null) {
            System.out.println("PICTURE DATA: " + data);
            String screenName = (String) data.get("screen_name"); //XXX this is not it
            ur = "https://api.twitter.com/1/users/profile_image?screen_name=" + screenName + "&size=bigger";
        }
        return ur;
    }

    @Override
    public RemoteUserInfo getRemoteUserInfo(LinkedinToken credential) throws IOException, JsonParseException, JsonMappingException, UnsupportedEncodingException {
        try {
            System.out.println("GET REMOTE USER INFO: " + credential);
            String tokenUrl = "https://www.linkedin.com/uas/oauth2/accessToken?"
                    + "grant_type=authorization_code&redirect_uri=" + getRedirectURL(credential.state)
                    + "&client_id=" + consumerKey + "&client_secret=" + consumerSecret;
            Waiter w = new Waiter();
            RH<LinkedinAuthToken> rh = new RH(w, LinkedinAuthToken.class);
            client.get().setURL(tokenUrl).on(StateType.Closed, w).execute(rh);
            LinkedinAuthToken auth = rh.get();
            assert auth != null : "Exception should have been thrown if no result";

            String reqUrl = "https://api.linkedin.com/v1/people/~?oauth2_access_token=" + auth.access_token;
            OAuthUtils utils = new OAuthUtils(this.consumerSecret, this.consumerKey);

            AuthorizationResponse r = new AuthorizationResponse(auth.access_token, null);

            String authHeader = utils.newSignatureBuilder().setToken(auth.access_token).buildSignature(Method.GET, "/v1/people/~", r);
            w = new Waiter();
            RH<Map> userInfo = new RH<Map>(w, Map.class);
            client.get().on(StateType.Closed, w).setURL(reqUrl).addHeader(Headers.AUTHORIZATION.toStringHeader(), authHeader).execute(userInfo);
            @SuppressWarnings("unchecked")
            Map<String,Object> info = userInfo.get();

            throw new UnsupportedOperationException("No RUI implemented yet for " + info);

        } catch (InterruptedException | GeneralSecurityException ex) {
            return Exceptions.chuck(ex);
        }
    }

    static class Waiter extends Receiver<State<?>> {

        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void receive(State<?> object) {
            latch.countDown();
        }
    }

    static class RH<T> extends ResponseHandler<T> {

        private final CountDownLatch latch = new CountDownLatch(1);
        private T result;
        private final Waiter waiter;

        public RH(Waiter waiter, Class<T> type) {
            super(type);
            this.waiter = waiter;
        }

        @Override
        protected void receive(HttpResponseStatus status, HttpHeaders headers, T obj) {
            latch.countDown();
            this.result = obj;
        }

        private Throwable throwable;
        private String errorMsg;

        @Override
        protected void onError(Throwable err) {
            this.throwable = err;
        }

        public T get() throws InterruptedException, IOException {
            waiter.latch.await(25, TimeUnit.SECONDS);
            latch.await(10, TimeUnit.SECONDS);
            if (throwable != null) {
                return Exceptions.chuck(throwable);
            }
            if (errorMsg != null) {
                throw new IOException(errorMsg);
            }
            return result;
        }

        @Override
        protected void onErrorResponse(String content) {
            errorMsg = content;
        }

    }

    public static class LinkedinAuthToken {

        public long expires_in = 0;
        public String access_token = "";
    }
}
