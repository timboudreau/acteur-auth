package com.mastfrog.acteur.auth;

import com.mastfrog.acteur.Event;

/**
 *
 * @author tim
 */
public class CookieAuthenticationStrategy extends AuthenticationStrategy {

    @Override
    protected Result<?> authenticate(Event evt) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void onAuthenticationFailed(Event evt) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
