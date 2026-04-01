package io.github.vxmqmqtt.vxmq.session;

import io.netty.handler.codec.mqtt.MqttQoS;

/**
 * QoS 1 delivery that has been sent and is awaiting PUBACK from the subscriber.
 */
public record InflightMessage(
        int packetId,
        String topicName,
        byte[] payload,
        MqttQoS qos,
        boolean retain,
        boolean duplicate,
        boolean fromOfflineQueue) {

    /**
     * Converts this inflight delivery back into a queued message for a later reconnect.
     */
    public QueuedMessage toQueuedMessage() {
        return new QueuedMessage(topicName, payload == null ? null : payload.clone(), qos, retain, true);
    }
}
