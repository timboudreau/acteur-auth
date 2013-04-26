package com.mastfrog.acteur.auth;

import com.mastfrog.acteur.Event;

/**
 * A strategy for authenticating a request
 *
 * @author Tim Boudreau
 */
public abstract class AuthenticationStrategy {
    /**
     * Determine if this strategy can be tried for this event
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
     * @return  a result
     */
    protected abstract Result<?> authenticate(Event evt);
    /**
     * Called back if this strategy and all others failed to authenticate
     * the request
     * @param evt 
     */
    protected abstract void onAuthenticationFailed(Event evt);
}
