package io.github.vxmqmqtt.vxmq.routing;

public record SubscriptionBinding(String clientId, String topicFilter, int requestedQos) {
}
