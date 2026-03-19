package io.github.vxmqmqtt.vxmq.protocol.model;

import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import java.util.List;

/**
 * Result of processing an inbound PUBLISH packet.
 */
public record PublishResult(
        boolean accepted,
        List<PublishDelivery> deliveries,
        boolean closeConnection,
        MqttDisconnectReasonCode disconnectReasonCode) {

    /**
     * Builds a successful publish result with the matched outbound deliveries.
     */
    public static PublishResult accepted(List<PublishDelivery> deliveries) {
        return new PublishResult(true, deliveries, false, null);
    }

    /**
     * Builds a rejected publish result that requires disconnecting the client.
     */
    public static PublishResult rejected(MqttDisconnectReasonCode disconnectReasonCode) {
        return new PublishResult(false, List.of(), true, disconnectReasonCode);
    }
}
