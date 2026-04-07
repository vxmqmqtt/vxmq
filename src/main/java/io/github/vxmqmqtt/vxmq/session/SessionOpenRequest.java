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
        WillMessage willMessage) {
}
