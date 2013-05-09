package com.mastfrog.acteur.auth;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Response;
import com.mastfrog.acteur.auth.UserFactory.Slug;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.settings.Settings;
import io.netty.handler.codec.http.Cookie;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author Tim Boudreau
 */
class CookieAuthenticationStrategy extends AuthenticationStrategy {

    private final UserFactory<?> users;
    private final OAuthPlugins plugins;

    @Inject
    CookieAuthenticationStrategy(Settings settings, UserFactory<?> users, OAuthPlugins plugins) {
        this.users = users;
        this.plugins = plugins;
    }

    @Override
    protected Result<?> authenticate(Event evt, AtomicReference<? super FailHook> onFail, Collection<? super Object> scopeContents, Response response) {
        Cookie[] cookies = evt.getHeader(Headers.COOKIE);
        if (cookies == null || cookies.length == 0) {
            return new Result(ResultType.NO_CREDENTIALS, true);
        }
        Result<?> res = null;
        for (Cookie ck : cookies) {
            String name = ck.getName();
            Optional<OAuthPlugin<?>> plugino = plugins.find(name);
            System.out.println("Find plugin for cookie " + name + "? " + plugino.isPresent());
            if (plugino.isPresent()) {
                OAuthPlugin<?> plugin = plugino.get();
                res = tryToAuthenticate(plugin, evt, ck, users, scopeContents, response);
                if (res.isSuccess()) {
                    scopeContents.add(res.user);
                    return res;
                }
            }
        }
        return res == null ? new Result(ResultType.NO_CREDENTIALS, true) : res;
    }

    private <T, R> Result<?> tryToAuthenticate(OAuthPlugin<T> plugin, Event evt, Cookie cookie, UserFactory<R> users, Collection<? super Object> scopeContents, Response response) {
        System.out.println("Try to authenticate " + plugin + " cookie " + cookie + " for " + evt.getPath());
        Optional<UserInfo> io = plugins.decodeCookieValue(cookie.getValue());
        System.out.println("DECODED USER INFO " + io);
        if (io.isPresent()) {
            UserInfo info = io.get();
            Optional<R> uo = users.findUserByName(info.userName);
            System.out.println("  find user " + info.userName + "? " + uo.isPresent());
            if (uo.isPresent()) {
                R user = uo.get();
                Optional<Slug> slugo = users.getSlug(plugin.code(), user, false);
                System.out.println("  looked up slug");
                if (slugo.isPresent()) {
                    Slug slug = slugo.get();
                    System.out.println("GOT THE SLUG " + slug);
                    if (slug.age().isLongerThan(plugin.getSlugMaxAge())) {
                        return new Result(ResultType.EXPIRED_CREDENTIALS, info.userName, true);
                    }
                    String matchWith = plugins.encodeCookieValue(info.userName, slug.slug).split(":")[0];
                    System.out.println("HASHED SLUG IS " + info.hashedSlug);
                    System.out.println("MATCH WITH " + matchWith);
                    System.out.println("COOKIE VALUE '" + info.hashedSlug + "' should match '"
                            + info.hashedSlug + "'" + " do they? "
                            + (info.hashedSlug.equals(matchWith))
                            + " length " + info.hashedSlug.length()
                            + " " + matchWith.length());

                    if (matchWith.equals(info.hashedSlug)) {
                        Object userObject = users.toUserObject(user);
                        String dn = users.getUserDisplayName(user);
                        if (dn != null && !plugins.hasDisplayNameCookie(evt)) {
                            plugins.createDisplayNameCookie(evt, response, dn);
                        }
                        return new Result(userObject, info.userName, matchWith, ResultType.SUCCESS, true, dn);
                    } else {
                        return new Result(ResultType.BAD_PASSWORD, info.userName, true);
                    }
                } else {
                    return new Result(ResultType.BAD_RECORD, info.userName, true);
                }
            } else {
                return new Result(ResultType.NO_RECORD, info.userName, true);
            }
        } else {
            return new Result(ResultType.INVALID_CREDENTIALS, true);
        }
    }
}
