package com.mastfrog.acteur.linkedin;

/**
 *
 * @author Tim Boudreau
 */
class OAuthResult {
    public final String token;
    public final String tokenSecret;
    public final String confirmed;

    public OAuthResult(String token, String tokenSecret, String confirmed) {
        this.token = token;
        this.tokenSecret = tokenSecret;
        this.confirmed = confirmed;
    }

    @Override
    public String toString() {
        return "OAuthResult{" + "token=" + token + ", tokenSecret=" + tokenSecret + ", confirmed=" + confirmed + '}';
    }

}
