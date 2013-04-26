package com.mastfrog.acteur.auth;

/**
 *
 * @author tim
 */
public enum ResultType {

    NO_CREDENTIALS, NO_RECORD, INVALID_CREDENTIALS, BAD_CREDENTIALS,
    BAD_RECORD, BAD_PASSWORD, SUCCESS;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
