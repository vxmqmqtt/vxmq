package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * CONNECT request that carries an unsupported protocol version while still preserving the raw metadata.
 */
public record UnsupportedConnectRequest(
        String requestedClientId,
        String protocolName,
        int protocolVersion,
        String username,
        String password,
        boolean passwordPresent,
        WillMessage willMessage) implements ConnectRequest {

    public UnsupportedConnectRequest(
            String requestedClientId,
            String protocolName,
            int protocolVersion,
            String username,
            boolean passwordPresent,
            WillMessage willMessage) {
        this(requestedClientId, protocolName, protocolVersion, username, null, passwordPresent, willMessage);
    }
}
