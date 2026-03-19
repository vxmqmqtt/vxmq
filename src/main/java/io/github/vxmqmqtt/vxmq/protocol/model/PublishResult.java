package io.github.vxmqmqtt.vxmq.protocol.model;

import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import java.util.List;

public record PublishResult(
        boolean accepted,
        List<PublishDelivery> deliveries,
        boolean closeConnection,
        MqttDisconnectReasonCode disconnectReasonCode) {

    public static PublishResult accepted(List<PublishDelivery> deliveries) {
        return new PublishResult(true, deliveries, false, null);
    }

    public static PublishResult rejected(MqttDisconnectReasonCode disconnectReasonCode) {
        return new PublishResult(false, List.of(), true, disconnectReasonCode);
    }
}
