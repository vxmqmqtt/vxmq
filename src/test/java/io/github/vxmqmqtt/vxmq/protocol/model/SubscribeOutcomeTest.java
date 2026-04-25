package io.github.vxmqmqtt.vxmq.protocol.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubscribeOutcomeTest {

    @Test
    void shouldExposeSubscribeAckViewsAndRetainedReplayPlan() {
        SubscribeAck ack = new SubscribeAck(List.of(
                SubscriptionItemResult.granted("sensors/+/temperature", MqttQoS.EXACTLY_ONCE),
                SubscriptionItemResult.rejected("status/#/bad", MqttSubAckReasonCode.TOPIC_FILTER_INVALID)));
        PublishDelivery retainedDelivery = new PublishDelivery(
                "client-sub",
                "sensors/room-1/temperature",
                "retained".getBytes(),
                MqttQoS.AT_MOST_ONCE,
                true,
                false,
                null,
                false);
        SubscribeOutcome outcome = new SubscribeOutcome(ack, new RetainedReplayPlan(List.of(retainedDelivery)));

        assertEquals(List.of(MqttQoS.EXACTLY_ONCE, MqttQoS.FAILURE), outcome.ack().grantedQosLevels());
        assertEquals(
                List.of(MqttSubAckReasonCode.GRANTED_QOS2, MqttSubAckReasonCode.TOPIC_FILTER_INVALID),
                outcome.ack().reasonCodes());
        assertEquals(List.of(retainedDelivery), outcome.retainedReplayPlan().deliveries());
    }

    @Test
    void shouldExposeUnsubscribeAckReasonCodes() {
        UnsubscribeAck ack = new UnsubscribeAck(List.of(
                UnsubscribeItemResult.success("sensors/+/temperature"),
                UnsubscribeItemResult.noSubscriptionExisted("status/+"),
                UnsubscribeItemResult.rejected("bad/#/filter", MqttUnsubAckReasonCode.TOPIC_FILTER_INVALID)));

        assertEquals(
                List.of(
                        MqttUnsubAckReasonCode.SUCCESS,
                        MqttUnsubAckReasonCode.NO_SUBSCRIPTION_EXISTED,
                        MqttUnsubAckReasonCode.TOPIC_FILTER_INVALID),
                ack.reasonCodes());
        assertTrue(ack.itemResults().stream().noneMatch(item -> item.reasonCode() == null));
    }
}
