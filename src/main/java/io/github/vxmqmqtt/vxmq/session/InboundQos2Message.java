package io.github.vxmqmqtt.vxmq.session;

import io.github.vxmqmqtt.vxmq.protocol.model.PublishProperties;

/**
 * Inbound QoS 2 publish transaction waiting for PUBREL from the publisher.
 */
public record InboundQos2Message(
        int packetId,
        String topicName,
        byte[] payload,
        boolean retain,
        boolean duplicate,
        PublishProperties properties) {

    public InboundQos2Message(
            int packetId,
            String topicName,
            byte[] payload,
            boolean retain,
            boolean duplicate) {
        this(packetId, topicName, payload, retain, duplicate, PublishProperties.empty());
    }

    public InboundQos2Message {
        properties = properties == null ? PublishProperties.empty() : properties;
    }

    public byte[] payloadCopy() {
        return payload == null ? null : payload.clone();
    }
}
