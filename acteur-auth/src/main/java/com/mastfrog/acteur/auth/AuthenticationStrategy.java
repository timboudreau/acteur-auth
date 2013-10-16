package com.mastfrog.acteur.auth;

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Response;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A strategy for authenticating a request
 *
 * @author Tim Boudreau
 */
@ImplementedBy(CompositeAuthenticationStrategy.class)
abstract class AuthenticationStrategy {

    /**
     * Determine if this strategy can be tried for this event
     *
     * @param evt An event
     * @return True if it should be tried
     */
    protected boolean isEnabled(HttpEvent evt) {
        return true;
    }

    /**
     * Authenticate
     *
     * @param evt An event
     * @return a result
     */
    protected abstract Result<?> authenticate(HttpEvent evt, AtomicReference<? super FailHook> onFail, Collection<? super Object> scopeContents, Response response);
    
    protected void authenticated(HttpEvent evt, Response response) {
        
    }

    public interface FailHook {

        /**
         * Called back if this strategy and all others failed to authenticate
         * the request
         *
         * @param evt
         */
        void onAuthenticationFailed(HttpEvent evt, Response response);
    }
}
