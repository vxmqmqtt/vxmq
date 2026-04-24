package io.github.vxmqmqtt.vxmq.protocol.model;

import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRecReasonCode;
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
        PublishAcknowledgementType acknowledgementType,
        MqttPubAckReasonCode pubAckReasonCode,
        MqttPubRecReasonCode pubRecReasonCode,
        boolean closeConnection,
        MqttDisconnectReasonCode disconnectReasonCode) {

    public boolean publishAcknowledge() {
        return acknowledgementType == PublishAcknowledgementType.PUBACK;
    }

    public boolean publishReceived() {
        return acknowledgementType == PublishAcknowledgementType.PUBREC;
    }

    /**
     * Builds a successful publish result with the matched outbound deliveries.
     */
    public static PublishResult accepted(
            List<PublishDelivery> deliveries,
            int queuedMessageCount,
            boolean publishAcknowledge,
            MqttPubAckReasonCode pubAckReasonCode) {
        PublishAcknowledgementType acknowledgementType =
                publishAcknowledge ? PublishAcknowledgementType.PUBACK : PublishAcknowledgementType.NONE;
        return new PublishResult(
                true,
                deliveries,
                queuedMessageCount,
                acknowledgementType,
                pubAckReasonCode,
                null,
                false,
                null);
    }

    /**
     * Builds a successful QoS 2 publish result that requires PUBREC before routing.
     */
    public static PublishResult qos2Received(MqttPubRecReasonCode pubRecReasonCode) {
        return new PublishResult(
                true,
                List.of(),
                0,
                PublishAcknowledgementType.PUBREC,
                null,
                pubRecReasonCode,
                false,
                null);
    }

    /**
     * Builds a rejected publish result that does not require disconnecting the client.
     */
    public static PublishResult rejectedWithoutDisconnect() {
        return new PublishResult(
                false,
                List.of(),
                0,
                PublishAcknowledgementType.NONE,
                null,
                null,
                false,
                null);
    }

    /**
     * Builds a rejected publish result that requires disconnecting the client.
     */
    public static PublishResult rejectedWithDisconnect(MqttDisconnectReasonCode disconnectReasonCode) {
        return new PublishResult(
                false,
                List.of(),
                0,
                PublishAcknowledgementType.NONE,
                null,
                null,
                true,
                disconnectReasonCode);
    }
}
