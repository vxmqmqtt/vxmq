package io.github.vxmqmqtt.vxmq.session;

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
        List<Integer> subscriptionIdentifiers) {

    public InflightMessage(
            int packetId,
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            boolean fromOfflineQueue) {
        this(packetId, topicName, payload, qos, retain, duplicate, fromOfflineQueue, null, List.of());
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
        this(packetId, topicName, payload, qos, retain, duplicate, fromOfflineQueue, qos2State, List.of());
    }

    public InflightMessage {
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
                subscriptionIdentifiers);
    }

    public byte[] payloadCopy() {
        return payload == null ? null : payload.clone();
    }
}
