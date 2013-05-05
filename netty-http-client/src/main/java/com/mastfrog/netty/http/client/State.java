package com.mastfrog.netty.http.client;

import com.mastfrog.url.URL;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

/**
 * Represents the current state of a request, used in notifications.
 * Subclass types can be used as keys for describing what events
 * to listen to in a way that carries type information.
 *
 * @author Tim Boudreau
 */
public abstract class State<T> {
    
    static final class Connecting extends State<Void> {

        Connecting() {
            super(Void.class, StateType.Connecting, null);
        }
    }

    static final class AwaitingResponse extends State<Void> {

        AwaitingResponse() {
            super(Void.class, StateType.AwaitingResponse, null);
        }
    }

    public static final class Connected extends State<Channel> {

        Connected(Channel channel) {
            super(Channel.class, StateType.Connected, channel);
        }
    }
    
    public static final class SendRequest extends State<HttpRequest> {

        SendRequest(HttpRequest req) {
            super(HttpRequest.class, StateType.SendRequest, req);
        }
    }

    public static final class HeadersReceived extends State<HttpResponse> {

        HeadersReceived(HttpResponse headers) {
            super(HttpResponse.class, StateType.HeadersReceived, headers);
        }
    }

    public static final class ContentReceived extends State<HttpContent> {

        ContentReceived(HttpContent headers) {
            super(HttpContent.class, StateType.ContentReceived, headers);
        }
    }

    public static final class FullContentReceived extends State<ByteBuf> {

        FullContentReceived(ByteBuf content) {
            super(ByteBuf.class, StateType.FullContentReceived, content);
        }
    }

    public static final class Redirect extends State<URL> {

        Redirect(URL content) {
            super(URL.class, StateType.Redirect, content);
        }
    }

    static final class Closed extends State<Void> {

        Closed() {
            super(Void.class, StateType.Closed, null);
        }
    }

    public static final class Finished extends State<FullHttpResponse> {

        Finished(FullHttpResponse buf) {
            super(FullHttpResponse.class, StateType.Finished, buf);
        }
    }

    public static final class Error extends State<Throwable> {

        Error(Throwable t) {
            super(Throwable.class, StateType.Error, t);
        }
    }

    static final class Cancelled extends State<Void> {

        Cancelled() {
            super(Void.class, StateType.Cancelled, null);
        }
    }
    private final Class<T> type;
    private final StateType name;
    private final T state;

    State(Class<T> type, StateType name, T state) {
        this.type = type;
        this.name = name;
        this.state = state;
    }

    public Class<T> type() {
        return type;
    }

    public String name() {
        return name.name();
    }

    public StateType stateType() {
        return name;
    }

    public T get() {
        return state;
    }

    @Override
    public String toString() {
        return name();
    }
}
