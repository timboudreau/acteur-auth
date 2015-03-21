package com.mastfrog.acteur.auth;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.ImplicitBindings;
import com.mastfrog.acteur.auth.MockUserFactory.MockUser;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.State;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.acteur.util.RequestID;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.concurrent.CountDownLatch;

/**
 *
 * @author tim
 */
@Singleton
@ImplicitBindings(MockUser.class)
public class MockApp extends Application {

    @Inject
    Hook hook;
    final UserFactory<?> uf;
    private final Provider<ApplicationControl> control;

    @Inject
    MockApp(OAuthPlugins plugins, UserFactory<?> uf, Provider<ApplicationControl> control) {
        add(plugins.bouncePageType());
        add(plugins.landingPageType());
        add(plugins.listOAuthProvidersPageType());
        add(plugins.testLoginPageType());
        add(AuthPage.class);
        add(SanityCheckPage.class);
        add(Application.helpPageType());
        this.uf = uf;
        this.control = control;
    }

    public CountDownLatch event(HttpEvent evt) {
        CountDownLatch latch = control.get().onEvent(evt, evt.getChannel());
        return latch;
    }

    @Override
    protected void onAfterRespond(RequestID id, Event<?> event, Acteur acteur, Page page, State state, HttpResponseStatus status, HttpResponse response) {
        if (hook != null) {
            hook.clear();
            hook.acteur = acteur;
            hook.page = page;
            hook.status = status;
            hook.state = state;
            hook.response = response;
            synchronized (hook) {
                hook.notifyAll();
            }
        }
    }

    @Override
    protected HttpResponse decorateResponse(Event<?> event, Page page, Acteur action, HttpResponse response) {
        return hook.response = super.decorateResponse(event, page, action, response); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected HttpResponse createNotFoundResponse(Event<?> event) {
        System.out.println("NOT FOUND: " + event);
        HttpResponse res = super.createNotFoundResponse(event);
        if (hook != null) {
            hook.clear();
            synchronized (hook) {
                hook.notifyAll();
            }
        }
        return res;
    }

    @Override
    public void onError(Throwable err) {
        err.printStackTrace();
        if (hook != null) {
            hook.clear();
            hook.error = err;
            synchronized (hook) {
                hook.notifyAll();
            }
        }
    }

    public static class Hook {

        public Acteur acteur;
        public Page page;
        public HttpResponseStatus status;
        public State state;
        public Throwable error;
        public HttpResponse response;

        public void await() throws Throwable {
            synchronized (this) {
                wait(3000);
            }
            if (error != null) {
                throw error;
            }
        }

        public <T> T getResponseHeader(HeaderValueType<T> type) {
            if (response == null) {
                return null;
            }
            String nm = type.name().toString();
            CharSequence s = response.headers().get(nm);
            T result = s == null ? null : type.toValue(s);
            return result;
        }

        public void clear() {
            acteur = null;
            page = null;
            status = null;
            state = null;
            response = null;
            error = null;
        }
    }

    static class SanityCheckPage extends Page {

        @Inject
        SanityCheckPage(ActeurFactory af) {
            add(af.matchMethods(Method.GET));
            add(af.matchPath("sanity$"));
            add(Success.class);
        }
    }

    static class Success extends Acteur {

        Success() {
            add(Headers.stringHeader("success"), "true");
            System.out.println("Sanity check write success");
            ok("SUCCESS");
        }
    }

    static class AuthPage extends Page {

        @Inject
        AuthPage(ActeurFactory af) {
            add(af.matchMethods(Method.GET));
            add(af.matchPath("boink$"));
            add(Auth.class);
            add(SuccessAfterAuth.class);
        }
    }

    static class SuccessAfterAuth extends Acteur {
        @Inject
        SuccessAfterAuth(MockUser user) {
            add(Headers.stringHeader("success"), "true");
            String result = "SUCCESS " + user;
            ok(result);
        }
    }
}
