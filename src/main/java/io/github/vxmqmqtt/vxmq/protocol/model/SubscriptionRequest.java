package io.github.vxmqmqtt.vxmq.protocol.model;

import java.util.List;

/**
 * Broker-facing view of one SUBSCRIBE packet.
 */
public record SubscriptionRequest(List<SubscriptionItem> items) {
}
