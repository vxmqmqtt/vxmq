package io.github.vxmqmqtt.vxmq.session;

import io.github.vxmqmqtt.vxmq.protocol.model.PublishProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.List;

/**
 * QoS 1 or QoS 2 delivery that is waiting for subscriber-side acknowledgement.
 */
public record InflightMessage(
        int packetId,
        String topicName,
        byte[] payload,
        MqttQoS qos,
        boolean retain,
        boolean duplicate,
        boolean fromOfflineQueue,
        OutboundQos2State qos2State,
        PublishProperties properties,
        List<Integer> subscriptionIdentifiers) {

    public InflightMessage(
            int packetId,
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            boolean fromOfflineQueue) {
        this(
                packetId,
                topicName,
                payload,
                qos,
                retain,
                duplicate,
                fromOfflineQueue,
                null,
                PublishProperties.empty(),
                List.of());
    }

    public InflightMessage(
            int packetId,
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            boolean fromOfflineQueue,
            OutboundQos2State qos2State) {
        this(
                packetId,
                topicName,
                payload,
                qos,
                retain,
                duplicate,
                fromOfflineQueue,
                qos2State,
                PublishProperties.empty(),
                List.of());
    }

    public InflightMessage(
            int packetId,
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            boolean fromOfflineQueue,
            OutboundQos2State qos2State,
            List<Integer> subscriptionIdentifiers) {
        this(
                packetId,
                topicName,
                payload,
                qos,
                retain,
                duplicate,
                fromOfflineQueue,
                qos2State,
                PublishProperties.empty(),
                subscriptionIdentifiers);
    }

    public InflightMessage {
        properties = properties == null ? PublishProperties.empty() : properties;
        subscriptionIdentifiers = List.copyOf(subscriptionIdentifiers);
    }

    /**
     * Converts this inflight delivery back into a queued message for a later reconnect.
     */
    public QueuedMessage toQueuedMessage() {
        return new QueuedMessage(
                topicName,
                payload == null ? null : payload.clone(),
                qos,
                retain,
                true,
                properties,
                subscriptionIdentifiers);
    }

    public InflightMessage withDuplicate(boolean newDuplicate) {
        return new InflightMessage(
                packetId,
                topicName,
                payloadCopy(),
                qos,
                retain,
                newDuplicate,
                fromOfflineQueue,
                qos2State,
                properties,
                subscriptionIdentifiers);
    }

    public InflightMessage withQos2State(OutboundQos2State newQos2State) {
        return new InflightMessage(
                packetId,
                topicName,
                payloadCopy(),
                qos,
                retain,
                duplicate,
                fromOfflineQueue,
                newQos2State,
                properties,
                subscriptionIdentifiers);
    }

    public byte[] payloadCopy() {
        return payload == null ? null : payload.clone();
    }
}
