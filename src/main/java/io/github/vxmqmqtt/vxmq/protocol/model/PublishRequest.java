package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * Broker-facing view of an inbound PUBLISH packet.
 */
public record PublishRequest(
        String topicName,
        int packetId,
        int qos,
        boolean retain,
        boolean duplicate,
        byte[] payload,
        PublishProperties properties,
        int packetSize) {

    public PublishRequest(
            String topicName,
            int packetId,
            int qos,
            boolean retain,
            boolean duplicate,
            byte[] payload) {
        this(topicName, packetId, qos, retain, duplicate, payload, PublishProperties.empty());
    }

    public PublishRequest(
            String topicName,
            int packetId,
            int qos,
            boolean retain,
            boolean duplicate,
            byte[] payload,
            PublishProperties properties) {
        this(
                topicName,
                packetId,
                qos,
                retain,
                duplicate,
                payload,
                properties,
                MqttPacketSizeEstimator.publishPacketSize(topicName, payload == null ? 0 : payload.length, qos, properties));
    }

    public PublishRequest {
        properties = properties == null ? PublishProperties.empty() : properties;
        if (packetSize < 0) {
            throw new IllegalArgumentException("packetSize must not be negative");
        }
    }

    /**
     * Returns the payload size without forcing callers to handle null payloads.
     */
    public int payloadSize() {
        return payload == null ? 0 : payload.length;
    }
}
