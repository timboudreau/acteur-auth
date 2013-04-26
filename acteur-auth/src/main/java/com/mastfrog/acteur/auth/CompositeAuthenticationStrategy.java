package com.mastfrog.acteur.auth;

import com.mastfrog.acteur.Event;
import java.util.ArrayList;
import java.util.List;

/**
 * Tries a list of authentiction strategies in order
 *
 * @author Tim Boudreau
 */
public final class CompositeAuthenticationStrategy extends AuthenticationStrategy {

    private final List<AuthenticationStrategy> all = new ArrayList<AuthenticationStrategy>();

    public CompositeAuthenticationStrategy(AuthenticationStrategy delegate) {
        all.add(delegate);
    }

    public CompositeAuthenticationStrategy add(AuthenticationStrategy delegate) {
        assert delegate != this && !(delegate instanceof CompositeAuthenticationStrategy);
        all.add(delegate);
        return this;
    }

    protected Result<?> authenticate(Event evt) {
        List<AuthenticationStrategy> fails = new ArrayList<AuthenticationStrategy>();
        Result res = null;
        for (AuthenticationStrategy a : all) {
            Result r = a.authenticate(evt);
            if (r.isSuccess()) {
                return r;
            } else {
                fails.add(a);
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
        for (AuthenticationStrategy a : fails) {
            a.onAuthenticationFailed(evt);
        }
        return res;
    }

    protected void onAuthenticationFailed(Event evt) {
        assert false : "Should only be called for non-composite instances";
    }
}
