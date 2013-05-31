package com.mastfrog.acteur.linkedin;

/**
 *
 * @author Tim Boudreau
 */
class AuthorizationResponse {

    public final String accessToken;
    public final String accessTokenSecret;

    public AuthorizationResponse(String accessToken, String accessTokenSecret) {
        this.accessToken = accessToken;
        this.accessTokenSecret = accessTokenSecret;
    }

    @Override
    public String toString() {
        return "AuthorizationResponse{" + "accessToken=" + accessToken + ", accessTokenSecret=" + accessTokenSecret + '}';
    }

}
