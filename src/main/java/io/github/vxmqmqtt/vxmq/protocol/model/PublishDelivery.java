package io.github.vxmqmqtt.vxmq.protocol.model;

import io.netty.handler.codec.mqtt.MqttQoS;

/**
 * Describes one outbound delivery generated from an inbound publish.
 */
public record PublishDelivery(String clientId, MqttQoS grantedQos) {
}
