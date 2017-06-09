package com.mastfrog.acteur.auth;

import com.google.inject.Inject;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.auth.FakeOAuthPlugin.FakeCredential;
import com.mastfrog.url.URL;
import com.mastfrog.util.Checks;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author tim
 */
class FakeOAuthPlugin extends OAuthPlugin<FakeCredential> {

    @Inject
    public FakeOAuthPlugin(String name, String code, String logoUrl, OAuthPlugins plugins) {
        super("fake", "fk", "http://timboudreau.com/images/logo.svg", plugins);
    }

    @Override
    public String getRedirectURL(UserFactory.LoginState state) {
        return URL.parse("http://localhost:3947/redirect?state=" + state.state).toString();
    }

    @Override
    public boolean revalidateCredential(String userName, String accessToken) {
        return false;
    }

    @Override
    public String stateForEvent(HttpEvent evt) {
        return evt.urlParameter("state");
    }

    @Override
    public FakeCredential credentialForEvent(HttpEvent evt) {
        FakeCredential fc = new FakeCredential(stateForEvent(evt));
        Info info = infos.get(fc);
        if (info == null) {
            info = new Info(++ix);
            infos.put(fc, info);
        }
        return fc;
    }

    int ix = 0;

    @Override
    public RemoteUserInfo getRemoteUserInfo(FakeCredential credential) {
        return new Info(++ix);
    }

    private final Map<FakeCredential, Info> infos = new HashMap<>();

    static class Info extends HashMap<String, Object> implements RemoteUserInfo {

        private final int index;

        Info(int index) {
            this.index = index;
        }

        @Override
        public String userName() {
            return "user" + index;
        }

        @Override
        public String displayName() {
            return "User " + index;
        }

        @Override
        public Object get(String key) {
            return super.get(key);
        }
    }

    public static class FakeCredential {

        public final String state;

        public FakeCredential(String state) {
            Checks.notNull("state", state);
            this.state = state;
        }

        public String toString() {
            return state;
        }

        public int hashCode() {
            return state.hashCode();
        }

        public boolean equals(Object o) {
            return o instanceof FakeCredential && ((FakeCredential) o).state.equals(state);
        }
    }
}
