package io.github.vxmqmqtt.vxmq.protocol.model;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;

/**
 * Rejected CONNACK view.
 */
public record RejectedConnectResponse(
        MqttConnectReturnCode returnCode,
        MqttProperties responseProperties) implements ConnectResponse {
}
