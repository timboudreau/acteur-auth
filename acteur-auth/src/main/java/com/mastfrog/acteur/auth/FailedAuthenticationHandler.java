package com.mastfrog.acteur.auth;

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Response;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 *
 * @author Tim Boudreau
 */
@ImplementedBy(FailedAuthenticationHandlerImpl.class)
public interface FailedAuthenticationHandler {
    public HttpResponseStatus onFailedAuthentication(HttpEvent evt, OAuthPlugins plugins, UserFactory<?> uf, Response response);
}
