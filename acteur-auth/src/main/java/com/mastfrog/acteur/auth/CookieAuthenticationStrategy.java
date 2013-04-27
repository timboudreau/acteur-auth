package com.mastfrog.acteur.auth;

import com.mastfrog.acteur.Event;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author tim
 */
public class CookieAuthenticationStrategy extends AuthenticationStrategy {

    @Override
    protected boolean isEnabled(Event evt) {
        return evt.getHeader(Headers.CO)
    }
    
    @Override
    protected Result<?> authenticate(Event evt, AtomicReference<? super FailHook> onFail, Collection<? super Object> scopeContents) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
