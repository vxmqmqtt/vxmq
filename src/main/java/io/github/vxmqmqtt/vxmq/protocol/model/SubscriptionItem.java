package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * One topic filter entry extracted from a SUBSCRIBE packet.
 */
public record SubscriptionItem(String topicFilter, int requestedQos) {
}
