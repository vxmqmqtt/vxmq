package io.github.vxmqmqtt.vxmq.protocol.model;

import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;

/**
 * Protocol outcome of one inbound PUBLISH packet.
 */
public record InboundPublishOutcome(
        DeliveryPlan deliveryPlan,
        PublishAcknowledgement acknowledgement,
        DisconnectAction disconnectAction) {

    public static InboundPublishOutcome completed(
            DeliveryPlan deliveryPlan,
            PublishAcknowledgement acknowledgement) {
        return new InboundPublishOutcome(deliveryPlan, acknowledgement, DisconnectAction.none());
    }

    public static InboundPublishOutcome deferred(PublishAcknowledgement acknowledgement) {
        return new InboundPublishOutcome(DeliveryPlan.empty(), acknowledgement, DisconnectAction.none());
    }

    public static InboundPublishOutcome rejected() {
        return new InboundPublishOutcome(DeliveryPlan.empty(), PublishAcknowledgement.none(), DisconnectAction.none());
    }

    public static InboundPublishOutcome rejectedWithDisconnect(MqttDisconnectReasonCode reasonCode) {
        return new InboundPublishOutcome(
                DeliveryPlan.empty(),
                PublishAcknowledgement.none(),
                DisconnectAction.disconnect(reasonCode));
    }
}
