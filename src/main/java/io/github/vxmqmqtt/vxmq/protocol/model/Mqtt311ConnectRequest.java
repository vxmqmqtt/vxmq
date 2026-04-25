package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * MQTT 3.1.1 CONNECT request model.
 */
public record Mqtt311ConnectRequest(
        String requestedClientId,
        String protocolName,
        boolean cleanSession,
        String username,
        boolean passwordPresent,
        WillMessage willMessage) implements ConnectRequest {

    @Override
    public int protocolVersion() {
        return 4;
    }
}
