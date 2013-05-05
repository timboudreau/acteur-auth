/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.mastfrog.netty.http.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.ssl.SslHandler;
import java.security.SecureRandom;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/**
 *
 * @author tim
 */
class Initializer extends ChannelInitializer<Channel> {

    private final ChannelInboundMessageHandlerAdapter<Object> handler;
    private final boolean ssl;
    private final int maxChunkSize;
    private final int maxInitialLineLength;
    private final boolean compress;

    public Initializer(ChannelInboundMessageHandlerAdapter<Object> handler, boolean ssl, int maxChunkSize, int maxInitialLineLength, int maxHeadersSize, boolean compress) {
        this.handler = handler;
        this.ssl = ssl;
        this.maxChunkSize = maxChunkSize;
        this.maxInitialLineLength = maxInitialLineLength;
        this.compress = compress;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        if (ssl) {
            SSLContext clientContext = SSLContext.getInstance("TLS");
            clientContext.init(null, TrivialTrustManagerFactory.getTrustManagers(), new SecureRandom());
            SSLEngine engine =
                    SecureChatSslContextFactory.getClientContext().createSSLEngine();

            engine.setUseClientMode(true);
            pipeline.addLast("ssl", new SslHandler(engine));
        }
        pipeline.addLast("http-codec", new HttpClientCodec(maxInitialLineLength, maxChunkSize, maxChunkSize));
        if (compress) {
            pipeline.addLast("decompressor", new HttpContentDecompressor());
        }
        pipeline.addLast("handler", handler);
    }
}
