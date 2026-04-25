package io.github.vxmqmqtt.vxmq.protocol.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.messages.codes.MqttPubCompReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRelReasonCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class Qos2FlowModelTest {

    @Test
    void shouldModelOutboundPubRecOutcome() {
        OutboundPubRecOutcome sendOutcome = OutboundPubRecOutcome.send(MqttPubRelReasonCode.SUCCESS);
        OutboundPubRecOutcome skipOutcome =
                OutboundPubRecOutcome.skip(MqttPubRelReasonCode.PACKET_IDENTIFIER_NOT_FOUND);

        assertEquals(PublishReleaseDisposition.SEND, sendOutcome.disposition());
        assertEquals(MqttPubRelReasonCode.SUCCESS, sendOutcome.reasonCode());
        assertEquals(PublishReleaseDisposition.SKIP, skipOutcome.disposition());
        assertEquals(MqttPubRelReasonCode.PACKET_IDENTIFIER_NOT_FOUND, skipOutcome.reasonCode());
    }

    @Test
    void shouldModelInboundPubRelOutcomeWithDeliveryPlan() {
        PublishDelivery delivery = new PublishDelivery(
                "subscriber",
                "sensors/room-1/temperature",
                "payload".getBytes(),
                MqttQoS.EXACTLY_ONCE,
                false,
                false,
                42,
                false);
        InboundPubRelOutcome outcome =
                InboundPubRelOutcome.completed(DeliveryPlan.of(List.of(delivery), 1));

        assertEquals(List.of(delivery), outcome.deliveryPlan().deliveries());
        assertEquals(1, outcome.deliveryPlan().queuedMessageCount());
        assertEquals(MqttPubCompReasonCode.SUCCESS, outcome.completionReasonCode());
    }

    @Test
    void shouldModelSessionResumeActions() {
        PublishDelivery delivery = new PublishDelivery(
                "subscriber",
                "status/client-a",
                "offline".getBytes(),
                MqttQoS.AT_LEAST_ONCE,
                false,
                true,
                7,
                true);
        SessionResumePlan plan = new SessionResumePlan(List.of(
                new ReplayPublish(delivery),
                new ReplayPubRel(9)));

        assertEquals(2, plan.actions().size());
        assertInstanceOf(ReplayPublish.class, plan.actions().getFirst());
        assertInstanceOf(ReplayPubRel.class, plan.actions().get(1));
        assertFalse(plan.isEmpty());
        assertTrue(SessionResumePlan.empty().isEmpty());
    }
}
