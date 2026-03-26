package io.github.vxmqmqtt.vxmq.session;

/**
 * Result of opening a broker session for a CONNECT request.
 */
public record SessionOpenResult(
        ClientSession session,
        boolean sessionPresent,
        ClientSession clearedSession) {
}
