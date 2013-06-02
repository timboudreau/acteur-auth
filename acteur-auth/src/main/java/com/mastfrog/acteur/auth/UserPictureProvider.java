package com.mastfrog.acteur.auth;

import com.google.inject.Inject;
import com.mastfrog.settings.Settings;

/**
 *
 * @author Tim Boudreau
 */
public class UserPictureProvider {

    public static final String DEFAULT_USER_PICTURE_URL = "user.default.picture.url";
    private final OAuthPlugins plugins;
    private final UserFactory<?> users;
    private final String defaultPicture;

    @Inject
    public UserPictureProvider(UserFactory<?> users, OAuthPlugins plugins, Settings settings) {
        this.users = users;
        this.plugins = plugins;
        this.defaultPicture = settings.getString(DEFAULT_USER_PICTURE_URL, "/scream.png");
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
        return defaultPicture;
    }
}
