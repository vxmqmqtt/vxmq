package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * MQTT 5 CONNECT request model.
 */
public record Mqtt5ConnectRequest(
        String requestedClientId,
        String protocolName,
        boolean cleanStart,
        long sessionExpiryIntervalSeconds,
        String username,
        boolean passwordPresent,
        WillMessage willMessage) implements ConnectRequest {

    @Override
    public int protocolVersion() {
        return 5;
    }
}
