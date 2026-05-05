package io.github.vxmqmqtt.vxmq.authn;

/**
 * Reason attached to an authentication result.
 */
public enum AuthnReason {
    SUCCESS,
    BAD_USERNAME_OR_PASSWORD,
    NOT_AUTHORIZED,
    NO_MATCH,
    BACKEND_ERROR
}
