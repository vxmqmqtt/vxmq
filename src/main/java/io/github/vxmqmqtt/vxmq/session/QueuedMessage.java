package io.github.vxmqmqtt.vxmq.session;

import io.github.vxmqmqtt.vxmq.protocol.model.PublishProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.List;

/**
 * Session-owned message that is waiting to be delivered to an offline client.
 */
public record QueuedMessage(
        String topicName,
        byte[] payload,
        MqttQoS qos,
        boolean retain,
        boolean duplicate,
        PublishProperties properties,
        List<Integer> subscriptionIdentifiers) {

    public QueuedMessage(String topicName, byte[] payload, MqttQoS qos, boolean retain, boolean duplicate) {
        this(topicName, payload, qos, retain, duplicate, PublishProperties.empty(), List.of());
    }

    public QueuedMessage(
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            List<Integer> subscriptionIdentifiers) {
        this(topicName, payload, qos, retain, duplicate, PublishProperties.empty(), subscriptionIdentifiers);
    }

    public QueuedMessage {
        properties = properties == null ? PublishProperties.empty() : properties;
        subscriptionIdentifiers = List.copyOf(subscriptionIdentifiers);
    }

    /**
     * Returns a defensive payload copy so queued state cannot be mutated externally.
     */
    public byte[] payloadCopy() {
        return payload == null ? null : payload.clone();
    }
}
