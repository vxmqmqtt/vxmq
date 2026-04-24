package io.github.vxmqmqtt.vxmq.protocol.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRecReasonCode;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for explicit inbound PUBLISH outcome modeling.
 */
class InboundPublishOutcomeTest {

    @Test
    void shouldBuildCompletedOutcomeWithoutAcknowledgement() {
        InboundPublishOutcome outcome = InboundPublishOutcome.completed(
                DeliveryPlan.of(java.util.List.of(), 1),
                PublishAcknowledgement.none());

        assertFalse(outcome.disconnectAction().isDisconnect());
        assertEquals(PublishAcknowledgementType.NONE, outcome.acknowledgement().type());
        assertEquals(1, outcome.deliveryPlan().queuedMessageCount());
    }

    @Test
    void shouldBuildCompletedOutcomeWithPubAck() {
        InboundPublishOutcome outcome = InboundPublishOutcome.completed(
                DeliveryPlan.empty(),
                PublishAcknowledgement.pubAck(MqttPubAckReasonCode.SUCCESS));

        assertFalse(outcome.disconnectAction().isDisconnect());
        assertEquals(PublishAcknowledgementType.PUBACK, outcome.acknowledgement().type());
        assertEquals(MqttPubAckReasonCode.SUCCESS, outcome.acknowledgement().mqtt5ReasonCode());
    }

    @Test
    void shouldBuildDeferredOutcomeWithPubRec() {
        InboundPublishOutcome outcome = InboundPublishOutcome.deferred(
                PublishAcknowledgement.pubRec(MqttPubRecReasonCode.SUCCESS));

        assertFalse(outcome.disconnectAction().isDisconnect());
        assertTrue(outcome.deliveryPlan().isEmpty());
        assertEquals(PublishAcknowledgementType.PUBREC, outcome.acknowledgement().type());
    }

    @Test
    void shouldBuildRejectedOutcomeWithoutDisconnect() {
        InboundPublishOutcome outcome = InboundPublishOutcome.rejected();

        assertFalse(outcome.disconnectAction().isDisconnect());
        assertTrue(outcome.deliveryPlan().isEmpty());
        assertEquals(PublishAcknowledgementType.NONE, outcome.acknowledgement().type());
    }

    @Test
    void shouldBuildRejectedOutcomeWithDisconnect() {
        InboundPublishOutcome outcome = InboundPublishOutcome.rejectedWithDisconnect(
                MqttDisconnectReasonCode.QOS_NOT_SUPPORTED);

        assertTrue(outcome.disconnectAction().isDisconnect());
        assertEquals(MqttDisconnectReasonCode.QOS_NOT_SUPPORTED, outcome.disconnectAction().reasonCode());
        assertTrue(outcome.deliveryPlan().isEmpty());
        assertEquals(PublishAcknowledgementType.NONE, outcome.acknowledgement().type());
    }
}
