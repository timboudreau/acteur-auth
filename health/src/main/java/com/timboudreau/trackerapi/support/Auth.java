package com.timboudreau.trackerapi.support;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.util.Realm;
import com.mastfrog.guicy.annotations.Defaults;
import io.netty.handler.codec.http.HttpResponseStatus;
import static com.timboudreau.trackerapi.support.ResultType.BAD_CREDENTIALS;
import io.netty.handler.codec.http.ServerCookieEncoder;
import java.util.Collections;
import java.util.Map;

/**
 * Handles authentication
 *
 * @author Tim Boudreau
 */
@Defaults("cookieSalt=hfh0w08988xsecret")
public final class Auth extends Acteur {

    private final Realm realm;

    @Inject
    Auth(Event evt, AuthSupport supp, Realm realm, GoogleAuth ga) {
        this.realm = realm;
        
//        AuthSupport.Result result = supp.getCookieResult();
//        if (!result.type.isSuccess()) {
//            result = supp.getAuthResult();
//        }
        Result result = supp.get();
        System.out.println("GOT " + result);
        if (!result.type.isSuccess()) {
//            add(Headers.WWW_AUTHENTICATE, realm);
            switch (result.type) {
                case NO_RECORD:
                case BAD_CREDENTIALS:
                case BAD_PASSWORD:
                    add(Headers.SET_COOKIE, supp.clearCookie());
                    setState(new RespondWith(HttpResponseStatus.UNAUTHORIZED,
                            "Incorrect user name or password: " + result.username + '\n'));
                    return;
                case INVALID_CREDENTIALS:
                    add(Headers.SET_COOKIE, supp.clearCookie());
                    setState(new RespondWith(HttpResponseStatus.UNAUTHORIZED,
                            "Illegal user name or password: " + result.username + '\n'));
                    return;
                case NO_CREDENTIALS:
                    setState(new RespondWith(HttpResponseStatus.UNAUTHORIZED,
                            "Authentication required for " + evt.getPath() + "\n"));
                    return;
                case BAD_RECORD:
                    setState(new RespondWith(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                            "Corrupted database record for " + result.username + "\n"));
                    return;
                default:
                    throw new AssertionError(result);
            }
        } else {
            if (!result.cookie) {
                add(Headers.SET_COOKIE, supp.encodeLoginCookie(result));
            }
            String other = supp.findCookie("dn");
            if (other != null) {
                String dn = result.user.displayName;
                if (dn != null) {
                    add(Headers.SET_COOKIE, supp.encodeDisplayNameCookie(dn));
                }
            }
            setState(new ConsumedLockedState(result, result.user));
        }
    }

    @Override
    public void describeYourself(Map<String, Object> into) {
        into.put("Requires Authentication", Collections.singletonMap("realm", realm.toString()));
    }
}
