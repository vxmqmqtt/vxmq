package io.github.vxmqmqtt.vxmq.protocol.model;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;

public record ConnectDecision(
        boolean accepted,
        MqttConnectReturnCode returnCode,
        boolean sessionPresent,
        String effectiveClientId,
        MqttProperties responseProperties,
        String supersededConnectionId) {

    public static ConnectDecision accept(
            String effectiveClientId,
            MqttProperties responseProperties,
            String supersededConnectionId) {
        return new ConnectDecision(
                true,
                MqttConnectReturnCode.CONNECTION_ACCEPTED,
                false,
                effectiveClientId,
                responseProperties,
                supersededConnectionId);
    }

    public static ConnectDecision reject(MqttConnectReturnCode returnCode) {
        return new ConnectDecision(
                false,
                returnCode,
                false,
                null,
                MqttProperties.NO_PROPERTIES,
                null);
    }
}
