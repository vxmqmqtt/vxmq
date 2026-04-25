package io.github.vxmqmqtt.vxmq.protocol.model;

import java.util.List;

/**
 * Retained messages that should be replayed after a successful subscribe.
 */
public record RetainedReplayPlan(List<PublishDelivery> deliveries) {

    public static RetainedReplayPlan empty() {
        return new RetainedReplayPlan(List.of());
    }
}
