package io.github.vxmqmqtt.vxmq.protocol.model;

import io.netty.handler.codec.mqtt.MqttQoS;

/**
 * Describes one outbound delivery generated from an inbound publish.
 */
public record PublishDelivery(
        String clientId,
        String topicName,
        byte[] payload,
        MqttQoS grantedQos,
        boolean retain,
        boolean duplicate,
        Integer packetId,
        boolean fromOfflineQueue) {

    /**
     * Returns a defensive payload copy so transport writes cannot mutate shared state.
     */
    public byte[] payloadCopy() {
        return payload == null ? null : payload.clone();
    }
}
