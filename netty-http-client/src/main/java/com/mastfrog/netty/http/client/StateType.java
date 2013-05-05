package com.mastfrog.netty.http.client;

import com.mastfrog.url.URL;
import com.mastfrog.util.thread.Receiver;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import java.lang.reflect.Method;

/**
 * Enumeration of states a request can be in.
 *
 * @author Tim Boudreau
 */
public enum StateType {
    /**
     * A connection has not been made yet.
     */
    Connecting, 
    /**
     * A connection has been made.
     */
    Connected, 
    /**
     * About to send a request
     */
    SendRequest,
    /**
     * The request has been sent.
     */
    AwaitingResponse, 
    /**
     * The response headers have been received, but the response body has not yet
     * (or there will not be one).
     */
    HeadersReceived, 
    /**
     * One chunk of content has been received - not necessarily the entire
     * response, but some content.
     */
    ContentReceived, 
    /**
     * The response was a 300-307 HTTP redirect and the redirect is being
     * followed. Note this event will only be seen if the HttpClient 
     * is set to follow redirects - otherwise, you will just see the 
     * redirect headers and body.
     */
    Redirect, 
    /**
     * The entire content of the response has arrived.
     */
    FullContentReceived, 
    /**
     * The connection was closed.
     */
    Closed, 
    /**
     * Similar to FullContentReceived, this event gives you a Netty
     * FullHttpRequest with the entire response.
     */
    Finished, 
    /**
     * An exception was thrown
     */
    Error, 
    /**
     * The call was cancelled;  useful for cleaning up resources.
     */
    Cancelled;

    Receiver<?> wrapperReceiver(Receiver<?> orig) {
        return wrapperReceiver(stateValueType(), orig);
    }

    private <T> Receiver<T> wrapperReceiver(final Class<T> type, final Receiver<?> orig) {
        return new Receiver<T>() {
            @Override
            @SuppressWarnings("unchecked")
            public void receive(T object) {
                Receiver r = orig;
                try {
                    r.receive(object);
                } catch (ClassCastException e) {
                    String typeName = null;
                    for (Method m : orig.getClass().getMethods()) {
                        if ("receive".equals(m.getName()) && m.getParameterTypes().length == 1) {
                            Class<?> what = m.getParameterTypes()[0];
                            if (what != Object.class) {
                                typeName = what.getName();
                                break;
                            }
                        }
                    }
                    System.err.println("Receiver for " 
                            + type.getName() + " takes the "
                            + "wrong class " + typeName + " in its receive() "
                            + "method. Passing null "
                            + "instead");
                    orig.receive(null);
                }
            }
        };
    }

    /**
     * Get the type of the State object tied to this event
     * @return a type
     */
    public Class<?> stateValueType() {
        switch (this) {
            case Connecting:
                return Void.class;
            case Connected:
                return Channel.class;
            case SendRequest :
                return HttpRequest.class;
            case AwaitingResponse:
                return Void.class;
            case HeadersReceived:
                return HttpResponse.class;
            case ContentReceived:
                return HttpContent.class;
            case Redirect:
                return URL.class;
            case FullContentReceived:
                return ByteBuf.class;
            case Closed:
                return Void.class;
            case Finished:
                return FullHttpResponse.class;
            case Error:
                return Throwable.class;
            case Cancelled:
                return Boolean.class;
            default:
                throw new AssertionError(this);
        }
    }

    /**
     * Get the type of the data payload of this event
     * @return a type
     */
    public Class<? extends State<?>> type() {
        switch (this) {
            case Connecting:
                return State.Connecting.class;
            case Connected:
                return State.Connected.class;
            case SendRequest :
                return State.SendRequest.class;
            case AwaitingResponse:
                return State.AwaitingResponse.class;
            case HeadersReceived:
                return State.HeadersReceived.class;
            case ContentReceived:
                return State.ContentReceived.class;
            case Redirect:
                return State.Redirect.class;
            case FullContentReceived:
                return State.FullContentReceived.class;
            case Closed:
                return State.Closed.class;
            case Finished:
                return State.Finished.class;
            case Error:
                return State.Error.class;
            case Cancelled:
                return State.Cancelled.class;
            default:
                throw new AssertionError(this);
        }
    }
    
}
