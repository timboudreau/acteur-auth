package com.mastfrog.acteur.auth;

import com.google.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
public class UserPictureProvider {

    private final OAuthPlugins plugins;
    private final UserFactory<?> users;

    @Inject
    public UserPictureProvider(UserFactory<?> users, OAuthPlugins plugins) {
        this.users = users;
        this.plugins = plugins;
    }

    public String getUserPictureURL(Object user) {
        return getPicture(users, user);
    }

    private <T> String getPicture(UserFactory<T> uf, Object u) {
        T obj = uf.type().cast(u);
        for (OAuthPlugin<?> ap : plugins) {
            String result = ap.getUserPictureURL(uf, obj);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
