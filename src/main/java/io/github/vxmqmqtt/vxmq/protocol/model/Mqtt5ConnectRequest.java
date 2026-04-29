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
        WillMessage willMessage,
        ConnectProperties properties) implements ConnectRequest {

    public Mqtt5ConnectRequest(
            String requestedClientId,
            String protocolName,
            boolean cleanStart,
            long sessionExpiryIntervalSeconds,
            String username,
            boolean passwordPresent,
            WillMessage willMessage) {
        this(requestedClientId, protocolName, cleanStart, sessionExpiryIntervalSeconds, username, passwordPresent,
                willMessage, ConnectProperties.empty());
    }

    public Mqtt5ConnectRequest(
            String requestedClientId,
            String protocolName,
            boolean cleanStart,
            long sessionExpiryIntervalSeconds,
            String username,
            boolean passwordPresent,
            WillMessage willMessage,
            MqttUserProperties userProperties) {
        this(requestedClientId, protocolName, cleanStart, sessionExpiryIntervalSeconds, username, passwordPresent,
                willMessage, new ConnectProperties(userProperties));
    }

    public Mqtt5ConnectRequest {
        properties = properties == null ? ConnectProperties.empty() : properties;
    }

    @Override
    public int protocolVersion() {
        return 5;
    }
}
