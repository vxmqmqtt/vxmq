package io.github.vxmqmqtt.vxmq.protocol.model;

import io.netty.handler.codec.mqtt.MqttQoS;

public record PublishDelivery(String clientId, MqttQoS grantedQos) {
}
