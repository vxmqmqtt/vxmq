package io.github.vxmqmqtt.vxmq.protocol.model;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;

/**
 * Response fields written back through CONNACK.
 */
public sealed interface ConnectResponse permits AcceptedConnectResponse, RejectedConnectResponse {

    MqttConnectReturnCode returnCode();

    MqttProperties responseProperties();
}
