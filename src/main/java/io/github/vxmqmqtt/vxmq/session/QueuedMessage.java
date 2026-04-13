package io.github.vxmqmqtt.vxmq.session;

import io.netty.handler.codec.mqtt.MqttQoS;

/**
 * Session-owned message that is waiting to be delivered to an offline client.
 */
public record QueuedMessage(
        String topicName,
        byte[] payload,
        MqttQoS qos,
        boolean retain,
        boolean duplicate) {

    /**
     * Returns a defensive payload copy so queued state cannot be mutated externally.
     */
    public byte[] payloadCopy() {
        return payload == null ? null : payload.clone();
    }
}
