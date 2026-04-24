package io.github.vxmqmqtt.vxmq.protocol.model;

import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;

/**
 * Connection action required after processing an inbound publish.
 */
public record DisconnectAction(
        boolean disconnect,
        MqttDisconnectReasonCode reasonCode) {

    public static DisconnectAction none() {
        return new DisconnectAction(false, null);
    }

    public static DisconnectAction disconnect(MqttDisconnectReasonCode reasonCode) {
        return new DisconnectAction(true, reasonCode);
    }

    public boolean isDisconnect() {
        return disconnect;
    }
}
