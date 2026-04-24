package io.github.vxmqmqtt.vxmq.protocol.model;

import io.vertx.mqtt.messages.codes.MqttPubRelReasonCode;

/**
 * Result of processing PUBREC from a subscriber for an outbound QoS 2 delivery.
 */
public record PubRecResult(
        boolean publishRelease,
        MqttPubRelReasonCode reasonCode) {

    public static PubRecResult release() {
        return new PubRecResult(true, MqttPubRelReasonCode.SUCCESS);
    }

    public static PubRecResult unknownPacketId() {
        return new PubRecResult(false, MqttPubRelReasonCode.PACKET_IDENTIFIER_NOT_FOUND);
    }
}
