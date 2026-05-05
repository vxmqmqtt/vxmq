package io.github.vxmqmqtt.vxmq.authz;

/**
 * Reason attached to an authorization result.
 */
public enum AuthzReason {
    SUCCESS,
    NOT_AUTHORIZED,
    NO_MATCH,
    BACKEND_ERROR
}
