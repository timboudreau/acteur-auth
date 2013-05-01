package com.mastfrog.acteur.auth;

import com.google.inject.ImplementedBy;
import com.mastfrog.acteur.Event;
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
    protected boolean isEnabled(Event evt) {
        return true;
    }

    /**
     * Authenticate
     *
     * @param evt An event
     * @return a result
     */
    protected abstract Result<?> authenticate(Event evt, AtomicReference<? super FailHook> onFail, Collection<? super Object> scopeContents);
    
    protected void authenticated(Event evt, Response response) {
        
    }

    public interface FailHook {

        /**
         * Called back if this strategy and all others failed to authenticate
         * the request
         *
         * @param evt
         */
        void onAuthenticationFailed(Event evt, Response response);
    }
}
