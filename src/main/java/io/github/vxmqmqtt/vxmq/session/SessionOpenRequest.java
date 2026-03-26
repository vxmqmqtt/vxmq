package io.github.vxmqmqtt.vxmq.session;

/**
 * Describes how CONNECT wants the broker to create or resume a session.
 */
public record SessionOpenRequest(
        boolean startFreshSession,
        boolean persistent,
        Long sessionExpiryIntervalSeconds,
        String connectionId) {
}
