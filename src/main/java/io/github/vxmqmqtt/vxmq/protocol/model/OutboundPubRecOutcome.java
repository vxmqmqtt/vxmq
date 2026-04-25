package io.github.vxmqmqtt.vxmq.protocol.model;

import io.vertx.mqtt.messages.codes.MqttPubRelReasonCode;

/**
 * Protocol result of processing PUBREC for an outbound QoS 2 message.
 */
public record OutboundPubRecOutcome(
        PublishReleaseDisposition disposition,
        MqttPubRelReasonCode reasonCode) {

    public static OutboundPubRecOutcome send(MqttPubRelReasonCode reasonCode) {
        return new OutboundPubRecOutcome(PublishReleaseDisposition.SEND, reasonCode);
    }

    public static OutboundPubRecOutcome skip(MqttPubRelReasonCode reasonCode) {
        return new OutboundPubRecOutcome(PublishReleaseDisposition.SKIP, reasonCode);
    }
}
