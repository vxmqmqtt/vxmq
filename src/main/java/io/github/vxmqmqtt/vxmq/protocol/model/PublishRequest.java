package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * Broker-facing view of an inbound PUBLISH packet.
 */
public record PublishRequest(String topicName, int qos, boolean retain, boolean duplicate, byte[] payload) {

    /**
     * Returns the payload size without forcing callers to handle null payloads.
     */
    public int payloadSize() {
        return payload == null ? 0 : payload.length;
    }
}
