package com.mastfrog.acteur.auth;

/**
 *
 * @author tim
 */
enum ResultType {

    NO_CREDENTIALS, NO_RECORD, INVALID_CREDENTIALS, BAD_CREDENTIALS,
    BAD_RECORD, BAD_PASSWORD, SUCCESS, EXPIRED_CREDENTIALS;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
