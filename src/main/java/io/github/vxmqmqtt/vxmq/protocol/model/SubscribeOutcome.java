package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * Protocol result of one SUBSCRIBE packet.
 */
public record SubscribeOutcome(
        SubscribeAck ack,
        RetainedReplayPlan retainedReplayPlan,
        DisconnectAction disconnectAction) {

    public SubscribeOutcome(SubscribeAck ack, RetainedReplayPlan retainedReplayPlan) {
        this(ack, retainedReplayPlan, DisconnectAction.none());
    }
}
