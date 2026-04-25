package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * Protocol result of one SUBSCRIBE packet.
 */
public record SubscribeOutcome(
        SubscribeAck ack,
        RetainedReplayPlan retainedReplayPlan) {
}
