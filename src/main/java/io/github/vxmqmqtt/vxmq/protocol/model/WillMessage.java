package io.github.vxmqmqtt.vxmq.protocol.model;

import io.netty.handler.codec.mqtt.MqttQoS;

/**
 * Broker-facing view of the MQTT Will payload negotiated during CONNECT.
 */
public record WillMessage(
        String topicName,
        byte[] payload,
        MqttQoS qos,
        boolean retain,
        PublishProperties properties) {

    public WillMessage(String topicName, byte[] payload, MqttQoS qos, boolean retain) {
        this(topicName, payload, qos, retain, PublishProperties.empty());
    }

    public WillMessage {
        properties = properties == null ? PublishProperties.empty() : properties;
    }

    /**
     * Returns a defensive copy of the will payload.
     */
    public byte[] payloadCopy() {
        return payload == null ? null : payload.clone();
    }
}
