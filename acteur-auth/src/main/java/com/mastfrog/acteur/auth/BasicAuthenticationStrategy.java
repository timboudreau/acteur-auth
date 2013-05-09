package com.mastfrog.acteur.auth;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Response;
import static com.mastfrog.acteur.auth.Auth.SKIP_HEADER;
import com.mastfrog.acteur.util.BasicCredentials;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.util.PasswordHasher;
import com.mastfrog.acteur.util.Realm;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author tim
 */
class BasicAuthenticationStrategy extends AuthenticationStrategy {

    private final Realm realm;
    private final UserFactory<?> users;
    private final PasswordHasher hasher;
    private final OAuthPlugins plugins;

    @Inject
    BasicAuthenticationStrategy(Realm realm, UserFactory<?> users, PasswordHasher hasher, OAuthPlugins plugins) {
        this.realm = realm;
        this.users = users;
        this.hasher = hasher;
        this.plugins = plugins;
    }

    @Override
    protected boolean isEnabled(Event evt) {
//        return !"true".equals(evt.getHeader(SKIP_HEADER));
        return true;
    }

    @Override
    protected Result<?> authenticate(Event evt, AtomicReference<? super FailHook> onFail, Collection<? super Object> scopeContents, Response response) {
        BasicCredentials credentials = evt.getHeader(Headers.AUTHORIZATION);
        if (credentials == null) {
            onFail.set(new FailHookImpl());
            return new Result<>(ResultType.NO_CREDENTIALS, false);
        }
        return tryAuthenticate(evt, users, credentials, onFail, scopeContents, response);
    }

    private <T> Result<?> tryAuthenticate(Event evt, UserFactory<T> uf, BasicCredentials credentials, AtomicReference<? super FailHook> onFail, Collection<? super Object> scopeContents, Response response) {
        Optional<T> u = uf.findUserByName(credentials.username);
        if (!u.isPresent()) {
            onFail.set(new FailHookImpl());
            return new Result<>(ResultType.NO_RECORD, credentials.username, false);
        }
        T user = u.get();
        String dn = uf.getUserDisplayName(user);
        Object userObject = uf.toUserObject(user);
        Optional<String> hasho = uf.getPasswordHash(user);
        if (!hasho.isPresent()) {
            System.out.println("NO PW ON " + user + " (" + user.getClass() + ")");
            return new Result<>(userObject, credentials.username, null, ResultType.BAD_RECORD, false, dn);
        }
        String hash = hasho.get();
        if (!hasher.checkPassword(credentials.password, hash)) {
            System.out.println("compare " + credentials.password + " and " + hash + " failed");
            return new Result<>(userObject, credentials.username, hash, ResultType.BAD_PASSWORD, false, dn);
        }
        scopeContents.add(credentials);
        scopeContents.add(userObject);
        if (dn != null && !plugins.hasDisplayNameCookie(evt)) {
            plugins.createDisplayNameCookie(evt, response, dn);
        }
        return new Result<>(userObject, credentials.username, hash, ResultType.SUCCESS, false, dn);
    }

    class FailHookImpl implements FailHook {

        @Override
        public void onAuthenticationFailed(Event evt, Response response) {
            if (!"true".equals(evt.getHeader(SKIP_HEADER))) {
                response.add(Headers.WWW_AUTHENTICATE, realm);
            }
        }
    }
}
