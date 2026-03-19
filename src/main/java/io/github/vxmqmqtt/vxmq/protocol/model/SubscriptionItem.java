package io.github.vxmqmqtt.vxmq.protocol.model;

public record SubscriptionItem(String topicFilter, int requestedQos) {
}
