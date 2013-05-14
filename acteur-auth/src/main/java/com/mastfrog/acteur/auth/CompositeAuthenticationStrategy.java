package com.mastfrog.acteur.auth;

import com.google.inject.Inject;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Response;
import com.mastfrog.settings.Settings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tries a list of authentiction strategies in order
 *
 * @author Tim Boudreau
 */
final class CompositeAuthenticationStrategy extends AuthenticationStrategy {

    private final List<AuthenticationStrategy> all = new ArrayList<>();

    public CompositeAuthenticationStrategy(AuthenticationStrategy delegate) {
        all.add(delegate);
    }

    @Inject
    public CompositeAuthenticationStrategy(BasicAuthenticationStrategy basic, CookieAuthenticationStrategy cookie, Settings settings) {
        if (settings.getBoolean(Auth.SETTINGS_KEY_ENABLE_COOKIE_AUTH, true)) {
            add(cookie);
        }
        if (settings.getBoolean(Auth.SETTINGS_KEY_ENABLE_BASIC_AUTH, true)) {
            add(basic);
        }
    }

    public CompositeAuthenticationStrategy add(AuthenticationStrategy delegate) {
        assert delegate != this && !(delegate instanceof CompositeAuthenticationStrategy);
        all.add(delegate);
        return this;
    }

    @Override
    protected Result<?> authenticate(Event evt, AtomicReference<? super FailHook> hook, Collection<? super Object> scopeContents, Response response) {
        List<AtomicReference<FailHook>> fails = new ArrayList<>();
        CompositeFailHook compositeHook = new CompositeFailHook(fails);
        hook.set(compositeHook);
        Result res = null;
        for (AuthenticationStrategy a : all) {
            System.out.println("TRY STRATEGY " + a);
            if (!a.isEnabled(evt)) {
                System.out.println("  not enabled, skip");
                continue;
            }
            Set<Object> s = new HashSet<>();
            AtomicReference<FailHook> ref = new AtomicReference<>();
            Result<?> r = a.authenticate(evt, ref, s, response);
            System.out.println("GOT " + r + " from " + a);
            if (r.isSuccess()) {
                scopeContents.addAll(s);
                hook.set(null);
                return r;
            } else {
                if (ref.get() != null) {
                    fails.add(ref);
                }
            }
            if (res == null) {
                res = r;
            } else {
                res = Result.combined(res, r);
            }
        }
        if (res == null) {
            res = new Result(ResultType.NO_CREDENTIALS, false);
        }
        return res;
    }

    private static final class CompositeFailHook implements FailHook {

        private final List<AtomicReference<FailHook>> all;

        public CompositeFailHook(List<AtomicReference<FailHook>> all) {
            this.all = all;
        }

        @Override
        public void onAuthenticationFailed(Event evt, Response response) {
            for (AtomicReference<FailHook> fh : all) {
                FailHook hook = fh.get();
                if (hook != null) {
                    hook.onAuthenticationFailed(evt, response);
                }
            }
        }
    }
}