package io.github.vxmqmqtt.vxmq.protocol.model;

import io.vertx.mqtt.messages.codes.MqttPubCompReasonCode;

/**
 * Protocol result of processing PUBREL for an inbound QoS 2 transaction.
 */
public record InboundPubRelOutcome(
        DeliveryPlan deliveryPlan,
        MqttPubCompReasonCode completionReasonCode) {

    public static InboundPubRelOutcome completed(DeliveryPlan deliveryPlan) {
        return new InboundPubRelOutcome(deliveryPlan, MqttPubCompReasonCode.SUCCESS);
    }

    public static InboundPubRelOutcome alreadyComplete() {
        return completed(DeliveryPlan.empty());
    }
}
