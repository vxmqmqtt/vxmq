package io.github.vxmqmqtt.vxmq.routing;

import io.netty.handler.codec.mqtt.MqttQoS;

/**
 * One stored subscription entry owned by a specific client.
 */
public record SubscriptionBinding(String clientId, String topicFilter, MqttQoS grantedQos) {
}
