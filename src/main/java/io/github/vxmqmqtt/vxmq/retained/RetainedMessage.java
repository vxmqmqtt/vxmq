package io.github.vxmqmqtt.vxmq.retained;

import io.netty.handler.codec.mqtt.MqttQoS;

/**
 * Retained message state stored independently from sessions and routing indexes.
 */
public record RetainedMessage(
        String topicName,
        byte[] payload,
        MqttQoS qos,
        boolean retain) {

    /**
     * Returns a defensive payload copy so callers cannot mutate retained state in place.
     */
    public byte[] payloadCopy() {
        return payload == null ? null : payload.clone();
    }
}
