package com.mastfrog.netty.http.client;

import com.mastfrog.util.Checks;
import java.util.LinkedList;
import java.util.List;

/**
 * Builds an HTTP client
 *
 * @author Tim Boudreau
 */
public final class HttpClientBuilder {

    private int threadCount = 8;
    private int maxChunkSize = 65536;
    private boolean compression = false;
    private int maxInitialLineLength = 2048;
    private int maxHeadersSize = 16384;
    private boolean followRedirects = true;
    private String userAgent;
    private final List<RequestInterceptor> interceptors = new LinkedList<>();

    /**
     * HTTP requests will transparently load a redirects.
     * Note that this means that handlers for events such as
     * Connected may be called more than once - once for each request
     * @return 
     */
    public HttpClientBuilder followRedirects() {
        followRedirects = true;
        return this;
    }

    public HttpClientBuilder dontFollowRedirects() {
        followRedirects = false;
        return this;
    }

    public HttpClientBuilder threadCount(int count) {
        Checks.nonNegative("threadCount", count);
        this.threadCount = count;
        return this;
    }

    public HttpClientBuilder maxChunkSize(int bytes) {
        Checks.nonNegative("bytes", bytes);
        this.maxChunkSize = bytes;
        return this;
    }

    public HttpClientBuilder maxInitialLineLength(int max) {
        maxInitialLineLength = max;
        return this;
    }

    public HttpClientBuilder maxHeadersSize(int max) {
        maxHeadersSize = max;
        return this;
    }

    public HttpClientBuilder useCompression() {
        compression = true;
        return this;
    }

    public HttpClientBuilder noCompression() {
        compression = false;
        return this;
    }

    public HttpClient build() {
        return new HttpClient(compression, maxChunkSize, threadCount,
                maxInitialLineLength, maxHeadersSize, followRedirects,
                userAgent, interceptors);
    }
    
    public HttpClientBuilder setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }
    
    public HttpClientBuilder addRequestInterceptor(RequestInterceptor interceptor) {
        this.interceptors.add(interceptor);
        return this;
    }
}
