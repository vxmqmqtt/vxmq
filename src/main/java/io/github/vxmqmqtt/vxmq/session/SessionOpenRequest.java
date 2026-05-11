package io.github.vxmqmqtt.vxmq.session;

import io.github.vxmqmqtt.vxmq.protocol.model.WillMessage;

/**
 * Describes how CONNECT wants the broker to create or resume a session.
 */
public record SessionOpenRequest(
        boolean startFreshSession,
        boolean persistent,
        Long sessionExpiryIntervalSeconds,
        String connectionId,
        WillMessage willMessage,
        int receiveMaximum) {

    public SessionOpenRequest(
            boolean startFreshSession,
            boolean persistent,
            Long sessionExpiryIntervalSeconds,
            String connectionId,
            WillMessage willMessage) {
        this(startFreshSession, persistent, sessionExpiryIntervalSeconds, connectionId, willMessage, 65_535);
    }

    public SessionOpenRequest {
        if (receiveMaximum < 1 || receiveMaximum > 65_535) {
            throw new IllegalArgumentException("receiveMaximum must be between 1 and 65535");
        }
    }
}
