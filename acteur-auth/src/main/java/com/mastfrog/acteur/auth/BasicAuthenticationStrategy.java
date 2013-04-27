package com.mastfrog.acteur.auth;

import com.google.inject.Inject;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Response;
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
public class BasicAuthenticationStrategy extends AuthenticationStrategy {

    public static final String SKIP_HEADER = "X-No-Basic-Auth";
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
        return !"true".equals(evt.getHeader(SKIP_HEADER));
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

    private <T> Result<T> tryAuthenticate(UserFactory<T> uf, BasicCredentials credentials, AtomicReference<? super FailHook> onFail, Collection<? super Object> scopeContents) {
        T user = uf.findUserByName(credentials.username);
        if (user == null) {
            onFail.set(new FailHookImpl());
            return new Result<>(ResultType.NO_RECORD, credentials.username, false);
        }
        String hash = uf.getPasswordHash(user);
        if (hash == null) {
            return new Result<>(user, credentials.username, hash, ResultType.BAD_RECORD, false);
        }
        if (!hasher.checkPassword(credentials.password, hash)) {
            return new Result<>(user, credentials.username, hash, ResultType.BAD_PASSWORD, false);
        }
        return new Result<>(user, credentials.username, hash, ResultType.SUCCESS, false);
    }

    class FailHookImpl implements FailHook {

        @Override
        public void onAuthenticationFailed(Event evt, Response response) {
            response.add(Headers.WWW_AUTHENTICATE, realm);
        }

    }

}
