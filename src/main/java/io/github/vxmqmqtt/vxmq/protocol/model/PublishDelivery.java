package io.github.vxmqmqtt.vxmq.protocol.model;

import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.List;

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
        boolean fromOfflineQueue,
        PublishProperties properties,
        List<Integer> subscriptionIdentifiers) {

    public PublishDelivery(
            String clientId,
            String topicName,
            byte[] payload,
            MqttQoS grantedQos,
            boolean retain,
            boolean duplicate,
            Integer packetId,
            boolean fromOfflineQueue) {
        this(
                clientId,
                topicName,
                payload,
                grantedQos,
                retain,
                duplicate,
                packetId,
                fromOfflineQueue,
                PublishProperties.empty(),
                List.of());
    }

    public PublishDelivery(
            String clientId,
            String topicName,
            byte[] payload,
            MqttQoS grantedQos,
            boolean retain,
            boolean duplicate,
            Integer packetId,
            boolean fromOfflineQueue,
            List<Integer> subscriptionIdentifiers) {
        this(
                clientId,
                topicName,
                payload,
                grantedQos,
                retain,
                duplicate,
                packetId,
                fromOfflineQueue,
                PublishProperties.empty(),
                subscriptionIdentifiers);
    }

    public PublishDelivery {
        properties = properties == null ? PublishProperties.empty() : properties;
        subscriptionIdentifiers = List.copyOf(subscriptionIdentifiers);
    }

    /**
     * Returns a defensive payload copy so transport writes cannot mutate shared state.
     */
    public byte[] payloadCopy() {
        return payload == null ? null : payload.clone();
    }
}
