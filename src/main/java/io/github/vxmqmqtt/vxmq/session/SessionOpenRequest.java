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
        int receiveMaximum,
        int maximumPacketSize) {

    public SessionOpenRequest(
            boolean startFreshSession,
            boolean persistent,
            Long sessionExpiryIntervalSeconds,
            String connectionId,
            WillMessage willMessage) {
        this(startFreshSession, persistent, sessionExpiryIntervalSeconds, connectionId, willMessage, 65_535, 268_435_455);
    }

    public SessionOpenRequest(
            boolean startFreshSession,
            boolean persistent,
            Long sessionExpiryIntervalSeconds,
            String connectionId,
            WillMessage willMessage,
            int receiveMaximum) {
        this(startFreshSession, persistent, sessionExpiryIntervalSeconds, connectionId, willMessage, receiveMaximum, 268_435_455);
    }

    public SessionOpenRequest {
        if (receiveMaximum < 1 || receiveMaximum > 65_535) {
            throw new IllegalArgumentException("receiveMaximum must be between 1 and 65535");
        }
        if (maximumPacketSize < 1 || maximumPacketSize > 268_435_455) {
            throw new IllegalArgumentException("maximumPacketSize must be between 1 and 268435455");
        }
    }
}
