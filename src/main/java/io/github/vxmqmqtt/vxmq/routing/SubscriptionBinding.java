package io.github.vxmqmqtt.vxmq.routing;

/**
 * One stored subscription entry owned by a specific client.
 */
public record SubscriptionBinding(String clientId, String topicFilter, int requestedQos) {
}
