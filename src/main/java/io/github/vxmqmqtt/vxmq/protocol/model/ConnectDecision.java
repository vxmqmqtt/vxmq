package io.github.vxmqmqtt.vxmq.protocol.model;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;

/**
 * Broker decision produced from a CONNECT packet.
 */
public record ConnectDecision(
        boolean accepted,
        MqttConnectReturnCode returnCode,
        boolean sessionPresent,
        String effectiveClientId,
        MqttProperties responseProperties,
        String supersededConnectionId) {

    /**
     * Builds a successful decision, optionally identifying a connection that must be taken over.
     */
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

    /**
     * Builds a rejected decision with the corresponding CONNACK return code.
     */
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
