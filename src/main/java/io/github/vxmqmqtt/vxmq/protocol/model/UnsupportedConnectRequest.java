package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * CONNECT request that carries an unsupported protocol version while still preserving the raw metadata.
 */
public record UnsupportedConnectRequest(
        String requestedClientId,
        String protocolName,
        int protocolVersion,
        String username,
        boolean passwordPresent,
        WillMessage willMessage) implements ConnectRequest {
}
