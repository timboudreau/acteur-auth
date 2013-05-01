package com.mastfrog.acteur.auth;

/**
 *
 * @author Tim Boudreau
 */
final class Result<UserType> {

    public final UserType user;
    public final String username;
    public final String hashedPass;
    public final String displayName;
    public final ResultType type;
    public final boolean cookie;

    public Result(ResultType type, String username, boolean cookie) {
        this(null, username, null, type, cookie,null);
    }

    public Result(ResultType type, boolean cookie) {
        this(null, null, null, type, cookie, null);
    }

    public Result(UserType user, String username, String hashedPass, ResultType type, boolean cookie, String displayName) {
        this.user = user;
        this.username = username;
        this.hashedPass = hashedPass;
        this.type = type;
        this.cookie = cookie;
        this.displayName = displayName;
    }

    static Result combined(Result a, Result b) {
        // We want cookie to be true if a cookie was present
        boolean ck = a.isSuccess() && a.cookie;
        if (!ck) {
            ck = b.isSuccess() && b.cookie;
        }
        return new Result(a.user == null ? b.user : null, a.username == null ? b.username : null, a.hashedPass == null ? b.hashedPass : a.hashedPass, a.type, ck, a.displayName == null ? b.displayName : a.displayName);
    }

    public boolean isSuccess() {
        return type.isSuccess();
    }

    @Override
    public String toString() {
        return "Result{" + "user=" + user + ", username=" + username + ", hashedPass=" + hashedPass + ", type=" + type + ", cookie=" + cookie + '}';
    }
}
