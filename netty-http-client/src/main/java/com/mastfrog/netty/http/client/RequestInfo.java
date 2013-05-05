package com.mastfrog.netty.http.client;

import com.mastfrog.url.URL;
import io.netty.handler.codec.http.HttpRequest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Tim Boudreau
 */
final class RequestInfo {
    final URL url;
    final HttpRequest req;
    final AtomicBoolean cancelled;
    final ResponseFuture handle;
    final ResponseHandler<?> r;
    final AtomicInteger redirectCount = new AtomicInteger();

    public RequestInfo(URL url, HttpRequest req, AtomicBoolean cancelled, ResponseFuture handle, ResponseHandler<?> r) {
        this.url = url;
        this.req = req;
        this.cancelled = cancelled;
        this.handle = handle;
        this.r = r;
    }
    
    @Override
    public String toString() {
        return "RequestInfo{" + "url=" + url + ", req=" + req + ", cancelled=" 
                + cancelled + ", handle=" + handle + ", r=" + r + '}';
    }
}
