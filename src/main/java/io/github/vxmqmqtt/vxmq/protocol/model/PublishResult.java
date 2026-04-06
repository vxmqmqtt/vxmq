package io.github.vxmqmqtt.vxmq.protocol.model;

import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import java.util.List;

/**
 * Result of processing an inbound PUBLISH packet.
 * accepted indicates whether the protocol layer accepted the packet as a valid publish operation.
 * closeConnection indicates whether the transport must close the client connection after processing.
 */
public record PublishResult(
        boolean accepted,
        List<PublishDelivery> deliveries,
        int queuedMessageCount,
        boolean publishAcknowledge,
        MqttPubAckReasonCode pubAckReasonCode,
        boolean closeConnection,
        MqttDisconnectReasonCode disconnectReasonCode) {

    /**
     * Builds a successful publish result with the matched outbound deliveries.
     */
    public static PublishResult accepted(
            List<PublishDelivery> deliveries,
            int queuedMessageCount,
            boolean publishAcknowledge,
            MqttPubAckReasonCode pubAckReasonCode) {
        return new PublishResult(true, deliveries, queuedMessageCount, publishAcknowledge, pubAckReasonCode, false, null);
    }

    /**
     * Builds a rejected publish result that does not require disconnecting the client.
     */
    public static PublishResult rejectedWithoutDisconnect() {
        return new PublishResult(false, List.of(), 0, false, null, false, null);
    }

    /**
     * Builds a rejected publish result that requires disconnecting the client.
     */
    public static PublishResult rejectedWithDisconnect(MqttDisconnectReasonCode disconnectReasonCode) {
        return new PublishResult(false, List.of(), 0, false, null, true, disconnectReasonCode);
    }
}
