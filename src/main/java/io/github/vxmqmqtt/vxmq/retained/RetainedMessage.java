package io.github.vxmqmqtt.vxmq.retained;

import io.github.vxmqmqtt.vxmq.protocol.model.PublishProperties;
import io.netty.handler.codec.mqtt.MqttQoS;

/**
 * Retained message state stored independently from sessions and routing indexes.
 */
public record RetainedMessage(
        String topicName,
        byte[] payload,
        MqttQoS qos,
        boolean retain,
        PublishProperties properties) {

    public RetainedMessage(String topicName, byte[] payload, MqttQoS qos, boolean retain) {
        this(topicName, payload, qos, retain, PublishProperties.empty());
    }

    public RetainedMessage {
        properties = properties == null ? PublishProperties.empty() : properties;
    }

    /**
     * Returns a defensive payload copy so callers cannot mutate retained state in place.
     */
    public byte[] payloadCopy() {
        return payload == null ? null : payload.clone();
    }
}
