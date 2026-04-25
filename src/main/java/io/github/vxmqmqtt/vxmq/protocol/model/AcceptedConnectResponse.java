package io.github.vxmqmqtt.vxmq.protocol.model;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;

/**
 * Successful CONNACK view.
 */
public record AcceptedConnectResponse(
        boolean sessionPresent,
        String effectiveClientId,
        MqttProperties responseProperties) implements ConnectResponse {

    @Override
    public MqttConnectReturnCode returnCode() {
        return MqttConnectReturnCode.CONNECTION_ACCEPTED;
    }
}
