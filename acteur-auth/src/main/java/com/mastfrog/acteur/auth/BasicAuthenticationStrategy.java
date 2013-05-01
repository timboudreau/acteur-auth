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

    @Inject
    BasicAuthenticationStrategy(Realm realm, UserFactory<?> users, PasswordHasher hasher) {
        this.realm = realm;
        this.users = users;
        this.hasher = hasher;
    }

    @Override
    protected boolean isEnabled(Event evt) {
//        return !"true".equals(evt.getHeader(SKIP_HEADER));
        return true;
    }

    @Override
    protected Result<?> authenticate(Event evt, AtomicReference<? super FailHook> onFail, Collection<? super Object> scopeContents) {
        BasicCredentials credentials = evt.getHeader(Headers.AUTHORIZATION);
        if (credentials == null) {
            onFail.set(new FailHookImpl());
            return new Result<>(ResultType.NO_CREDENTIALS, false);
        }
        return tryAuthenticate(users, credentials, onFail, scopeContents);
    }

    private <T> Result<?> tryAuthenticate(UserFactory<T> uf, BasicCredentials credentials, AtomicReference<? super FailHook> onFail, Collection<? super Object> scopeContents) {
        Optional<T> u = uf.findUserByName(credentials.username);
        if (!u.isPresent()) {
            onFail.set(new FailHookImpl());
            return new Result<>(ResultType.NO_RECORD, credentials.username, false);
        }
        T user = u.get();
        Object userObject = uf.toUserObject(user);
        Optional<String> hasho = uf.getPasswordHash(user);
        if (!hasho.isPresent()) {
            return new Result<>(userObject, credentials.username, null, ResultType.BAD_RECORD, false);
        }
        String hash = hasho.get();
        if (!hasher.checkPassword(credentials.password, hash)) {
            scopeContents.add(credentials);
            scopeContents.add(userObject);
            return new Result<>(userObject, credentials.username, hash, ResultType.BAD_PASSWORD, false);
        }
        return new Result<>(userObject, credentials.username, hash, ResultType.SUCCESS, false);
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
