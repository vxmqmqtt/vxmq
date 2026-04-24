package io.github.vxmqmqtt.vxmq.session;

/**
 * Inbound QoS 2 publish transaction waiting for PUBREL from the publisher.
 */
public record InboundQos2Message(
        int packetId,
        String topicName,
        byte[] payload,
        boolean retain,
        boolean duplicate) {

    public byte[] payloadCopy() {
        return payload == null ? null : payload.clone();
    }
}
