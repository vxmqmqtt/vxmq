package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * Broker-facing view of a CONNECT packet.
 */
public record ConnectRequest(
        String requestedClientId,
        String protocolName,
        int protocolVersion,
        boolean cleanSession,
        String username,
        boolean passwordPresent) {

    /**
     * Returns whether the request uses MQTT 3.1.1.
     */
    public boolean isMqtt311() {
        return protocolVersion == 4;
    }

    /**
     * Returns whether the request uses MQTT 5.
     */
    public boolean isMqtt5() {
        return protocolVersion == 5;
    }
}
