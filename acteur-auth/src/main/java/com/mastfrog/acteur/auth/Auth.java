package com.mastfrog.acteur.auth;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.auth.AuthenticationStrategy.FailHook;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Acteur which does cookie-based and/or basic authentication.  The two
 * settings constants defined on this class let you determine if Basic and/or
 * cookie authentication are used.  Note that cookie authentication is
 * required for OAuth to work.
 * <p/>
 * Clients such as browser-based clients which want to guarantee that the
 * user never sees a browser-based authentication popup can set the header
 * <code>X-No-Authenticate</code> to <code>true</code> in their requests.
 * This will suppress sending the <code>WWW-Authenticate</code> response header
 * that triggers this popup in most browsers.  Such clients must take responsibility
 * for prompting the user to login when they get an <code>401 Unauthorized</code>
 * response from web api calls.
 *
 * @author Tim Boudreau
 */
public class Auth extends Acteur {

    /**
     * Settings key for boolean property determining whether HTTP Basic
     * authentication can be used.
     */
    public static final String SETTINGS_KEY_ENABLE_BASIC_AUTH = "basic.auth";
    /**
     * Settings key for boolean property determining whether Cookies are used
     * to authenticate.
     */
    public static final String SETTINGS_KEY_ENABLE_COOKIE_AUTH = "cookie.auth";
    /**
     * Header which clients can send to suppress the <code>WWW-Authenticate</code>
     * response header.
     */
    public static final String SKIP_HEADER = "X-No-Authenticate";

    @Inject
    Auth(AuthenticationStrategy strategy, Event evt, UserFactory<?> uf, OAuthPlugins plugins) {
        AtomicReference<FailHook> hook = new AtomicReference<>();
        List<Object> contents = new LinkedList<>();
        Result<?> authenticationResult = strategy.authenticate(evt, hook, contents);
        if (!authenticationResult.isSuccess()) {
            FailHook hookImpl = hook.get();
            if (hookImpl != null) {
                hookImpl.onAuthenticationFailed(evt, response());
            }
            setState(new RespondWith(HttpResponseStatus.UNAUTHORIZED, 
                    authenticationResult.type.toString()));
        } else {
            setupCookie(evt, plugins, authenticationResult);
            setState(new ConsumedLockedState(
                    contents.toArray(new Object[contents.size()])));
        }
    }
    
    private <T> void setupCookie(Event evt, OAuthPlugins plugins, Result<?> result) {
            plugins.createDisplayNameCookie(evt, response(), result.displayName);
    }
}
