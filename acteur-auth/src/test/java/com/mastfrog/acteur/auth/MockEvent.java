package com.mastfrog.acteur.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.util.HeaderValueType;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.util.Method;
import com.mastfrog.url.Path;
import com.mastfrog.util.Exceptions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.joda.time.DateTime;

/**
 *
 * @author Tim Boudreau
 */
class MockEvent implements Event {

    private final Path path;
    private final Map<String, String> headers = new HashMap<>();
    private final Map<String, String> parameters = new HashMap<>();
    private String body;
    private Method method = Method.GET;
    List<Object> written = new LinkedList<>();

    public MockEvent(Path path) {
        this.path = path;
        headers.put("Host", "localhost");
        headers.put("Date", Headers.DATE.toString(DateTime.now()));
    }

    public MockEvent(String pth) {
        this(Path.parse(pth));
    }

    public MockEvent addParameter(String name, String val) {
        parameters.put(name, val);
        return this;
    }

    public MockEvent addHeader(String name, String val) {
        headers.put(name, val);
        return this;
    }
    
    public <T> MockEvent add(HeaderValueType<T> type, T obj) {
        headers.put(type.name(), type.toString(obj));
        return this;
    }

    public MockEvent setBody(String body) {
        this.body = body;
        return this;
    }

    public MockEvent setMethod(Method method) {
        this.method = method;
        return this;
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public Optional<Integer> getIntParameter(String name) {
        String val = getParameter(name);
        if (val != null) {
            int ival = Integer.parseInt(val);
            return Optional.of(ival);
        }
        return Optional.absent();
    }

    @Override
    public Optional<Long> getLongParameter(String name) {
        String val = getParameter(name);
        if (val != null) {
            long lval = Long.parseLong(val);
            return Optional.of(lval);
        }
        return Optional.absent();
    }

    private final CountDownLatch onClose = new CountDownLatch(1);

    public void awaitClose() throws InterruptedException {
        onClose.await();
    }

    public String getWrittenResponse() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Object o : written) {
            if (o instanceof ByteBuf) {
                ByteBuf b = (ByteBuf) o;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                b.readBytes(baos, b.readableBytes());
                sb.append(new String(baos.toByteArray(), "UTF-8"));
            }
//            sb.append(o);
        }
        return sb.toString();
    }

    public Channel channel() {
        return getChannel();
    }

    @Override
    public Channel getChannel() {
        return new Channel() {
            @Override
            public Integer id() {
                return 1;
            }

            @Override
            public EventLoop eventLoop() {
                return null;
            }

            @Override
            public Channel parent() {
                return this;
            }

            @Override
            public ChannelConfig config() {
                return null;
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public boolean isRegistered() {
                return true;
            }

            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public ChannelMetadata metadata() {
                return null;
            }
            private ByteBuf out = Unpooled.buffer();

            @Override
            public ByteBuf outboundByteBuffer() {
                return out;
            }

            @Override
            public <T> MessageBuf<T> outboundMessageBuffer() {
                return null;
            }

            @Override
            public SocketAddress localAddress() {
                return new InetSocketAddress(1);
            }

            @Override
            public SocketAddress remoteAddress() {
                return new InetSocketAddress(2);
            }

            @Override
            public ChannelFuture closeFuture() {
                final Channel t = this;
                return new ChannelFuture() {
                    @Override
                    public Channel channel() {
                        return t;
                    }

                    @Override
                    public boolean isDone() {
                        return true;
                    }

                    @Override
                    public boolean isSuccess() {
                        return true;
                    }

                    @Override
                    public Throwable cause() {
                        return null;
                    }

                    @Override
                    public ChannelFuture addListener(GenericFutureListener<? extends Future<Void>> listener) {
                        try {
                            GenericFutureListener f = listener;
                            f.operationComplete(this);
                        } catch (Exception ex) {
                            return Exceptions.chuck(ex);
                        }
                        return this;
                    }

                    public ChannelFuture addListeners(GenericFutureListener<? extends Future<Void>>... listeners) {
                        for (GenericFutureListener<? extends Future<Void>> l : listeners) {
                            try {
                                GenericFutureListener f = l;
                                f.operationComplete(this);
                            } catch (Exception ex) {
                                return Exceptions.chuck(ex);
                            }
                        }
                        return this;
                    }

                    public ChannelFuture removeListener(GenericFutureListener<? extends Future<Void>> listener) {
                        return this;
                    }

                    public ChannelFuture removeListeners(GenericFutureListener<? extends Future<Void>>... listeners) {
                        return this;
                    }

                    @Override
                    public ChannelFuture sync() throws InterruptedException {
                        return this;
                    }

                    @Override
                    public ChannelFuture syncUninterruptibly() {
                        return this;
                    }

                    @Override
                    public ChannelFuture await() throws InterruptedException {
                        return this;
                    }

                    @Override
                    public ChannelFuture awaitUninterruptibly() {
                        return this;
                    }

                    @Override
                    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
                        return true;
                    }

                    @Override
                    public boolean await(long timeoutMillis) throws InterruptedException {
                        return true;
                    }

                    @Override
                    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
                        return true;
                    }

                    @Override
                    public boolean awaitUninterruptibly(long timeoutMillis) {
                        return true;
                    }

                    @Override
                    public Void getNow() {
                        return null;
                    }

                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning) {
                        return true;
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }

                    @Override
                    public Void get() throws InterruptedException, ExecutionException {
                        return null;
                    }

                    @Override
                    public Void get(long l, TimeUnit tu) throws InterruptedException, ExecutionException, TimeoutException {
                        return null;
                    }
                };
            }

            @Override
            public Channel.Unsafe unsafe() {
                return null;
            }

            @Override
            public <T> Attribute<T> attr(AttributeKey<T> key) {
                return null;
            }

            @Override
            public ChannelFuture bind(SocketAddress localAddress) {
                return closeFuture();
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress) {
                return closeFuture();
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
                return closeFuture();
            }

            @Override
            public ChannelFuture disconnect() {
                return closeFuture();
            }

            @Override
            public ChannelFuture close() {
                onClose.countDown();
                return closeFuture();
            }

            @Override
            public ChannelFuture deregister() {
                return closeFuture();
            }

            @Override
            public ChannelFuture flush() {
                return closeFuture();
            }

            @Override
            public ChannelFuture write(Object message) {
                System.out.println("WRITE " + message.getClass().getName() + ": " + message);
                written.add(message);
                return closeFuture();
            }

            @Override
            public ChannelFuture sendFile(FileRegion region) {
                return closeFuture();
            }

            @Override
            public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
                return closeFuture();
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
                return closeFuture();
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                return closeFuture();
            }

            @Override
            public ChannelFuture disconnect(ChannelPromise promise) {
                close();
                return closeFuture();
            }

            @Override
            public ChannelFuture close(ChannelPromise promise) {
                close();
                return closeFuture();
            }

            @Override
            public ChannelFuture deregister(ChannelPromise promise) {
                return closeFuture();
            }

            @Override
            public void read() {
            }

            @Override
            public ChannelFuture flush(ChannelPromise promise) {
                return closeFuture();
            }

            @Override
            public ChannelFuture write(Object message, ChannelPromise promise) {
                written.add(message);
                promise.setSuccess();
                return closeFuture();
            }

            @Override
            public ChannelFuture sendFile(FileRegion region, ChannelPromise promise) {
                return closeFuture();
            }

            @Override
            public ChannelPipeline pipeline() {
                return null;
            }

            @Override
            public ByteBufAllocator alloc() {
                return UnpooledByteBufAllocator.DEFAULT;
            }

            @Override
            public ChannelPromise newPromise() {
                return null;
            }

            @Override
            public ChannelFuture newSucceededFuture() {
                return closeFuture();
            }

            @Override
            public ChannelFuture newFailedFuture(Throwable cause) {
                return closeFuture();
            }

            @Override
            public int compareTo(Channel t) {
                return 0;
            }

            public boolean isWritable() {
                return true;
            }

            public ChannelProgressivePromise newProgressivePromise() {
                throw new UnsupportedOperationException();
            }

            public ChannelPromise voidPromise() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public HttpMessage getRequest() {
        String pth = this.path.toString();
        DefaultHttpRequest m = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method.name()), pth) {

            @Override
            public HttpHeaders headers() {
                return new HttpHeaders() {

                    @Override
                    public String get(String name) {
                        return headers.get(name);
                    }

                    @Override
                    public List<String> getAll(String name) {
                        List<String> l = new ArrayList<String>();
                        for (Map.Entry<String, String> e : headers.entrySet()) {
                            l.add(e.getKey() + ": " + e.getValue());
                        }
                        return l;
                    }

                    @Override
                    public List<Map.Entry<String, String>> entries() {
                        return new LinkedList<>(headers.entrySet());
                    }

                    @Override
                    public boolean contains(String name) {
                        return headers.containsKey(name);
                    }

                    @Override
                    public boolean isEmpty() {
                        return headers.isEmpty();
                    }

                    @Override
                    public Set<String> names() {
                        return headers.keySet();
                    }

                    @Override
                    public HttpHeaders add(String name, Object value) {
                        headers.put(name, value + "");
                        return this;
                    }

                    @Override
                    public HttpHeaders add(String name, Iterable<?> values) {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public HttpHeaders set(String name, Object value) {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public HttpHeaders set(String name, Iterable<?> values) {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public HttpHeaders remove(String name) {
                        headers.remove(name);
                        return this;
                    }

                    @Override
                    public HttpHeaders clear() {
                        headers.clear();
                        return this;
                    }

                    @Override
                    public Iterator<Map.Entry<String, String>> iterator() {
                        return headers.entrySet().iterator();
                    }
                };
            }
        };
        return m;
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return new InetSocketAddress(2);
    }

    @Override
    public String getHeader(String nm) {
        return headers.get(nm);
    }

    @Override
    public String getParameter(String param) {
        return parameters.get(param);
    }

    @Override
    public <T> T getHeader(HeaderValueType<T> value) {
        String val = headers.get(value.name());
        if (val == null) {
            return null;
        }
        return value.toValue(val);
    }

    @Override
    public Map<String, String> getParametersAsMap() {
        return new HashMap<>(headers);
    }

    @Override
    public <T> T getParametersAs(Class<T> type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getContentAsJSON(Class<T> type) throws IOException {
        return body == null ? null : new ObjectMapper().readValue(body, type);
    }

    @Override
    public ByteBuf getContent() throws IOException {
        return body == null ? Unpooled.buffer() : Unpooled.copiedBuffer(body, CharsetUtil.UTF_8);
    }

    @Override
    public boolean isKeepAlive() {
        return false;
    }

    @Override
    public OutputStream getContentAsStream() throws IOException {
        return new ByteArrayOutputStream();
    }
}
