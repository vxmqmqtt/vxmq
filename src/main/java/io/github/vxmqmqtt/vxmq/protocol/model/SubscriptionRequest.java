package io.github.vxmqmqtt.vxmq.protocol.model;

import java.util.List;

/**
 * Broker-facing view of one SUBSCRIBE packet.
 */
public record SubscriptionRequest(
        List<SubscriptionItem> items,
        SubscriptionProperties properties) {

    public SubscriptionRequest(List<SubscriptionItem> items) {
        this(items, SubscriptionProperties.empty());
    }

    public SubscriptionRequest(List<SubscriptionItem> items, MqttUserProperties userProperties) {
        this(items, new SubscriptionProperties(userProperties));
    }

    public SubscriptionRequest {
        items = List.copyOf(items);
        properties = properties == null ? SubscriptionProperties.empty() : properties;
    }
}
