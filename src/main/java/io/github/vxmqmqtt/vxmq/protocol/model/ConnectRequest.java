package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * Broker-facing view of a CONNECT packet with protocol-specific shape preserved per version.
 */
public sealed interface ConnectRequest
        permits Mqtt311ConnectRequest, Mqtt5ConnectRequest, UnsupportedConnectRequest {

    String requestedClientId();

    String protocolName();

    int protocolVersion();

    String username();

    boolean passwordPresent();

    WillMessage willMessage();

    default ConnectProperties properties() {
        return ConnectProperties.empty();
    }

    default boolean isMqtt311() {
        return this instanceof Mqtt311ConnectRequest;
    }

    default boolean isMqtt5() {
        return this instanceof Mqtt5ConnectRequest;
    }
}
