package com.mastfrog.acteur.auth;

import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Response;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 *
 * @author Tim Boudreau
 */
final class FailedAuthenticationHandlerImpl implements FailedAuthenticationHandler {

    @Override
    public HttpResponseStatus onFailedAuthentication(HttpEvent evt, OAuthPlugins plugins, UserFactory<?> uf, Response response) {
        return HttpResponseStatus.UNAUTHORIZED;
    }
}
