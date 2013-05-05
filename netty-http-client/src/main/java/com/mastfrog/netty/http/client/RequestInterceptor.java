package com.mastfrog.netty.http.client;

import io.netty.handler.codec.http.HttpRequest;

/**
 * Object which can be attached to an HttpClient which intercept all requests
 * and can modify them before they are sent
 *
 * @author Tim Boudreau
 */
public interface RequestInterceptor {
    public HttpRequest intercept(HttpRequest req);
}
