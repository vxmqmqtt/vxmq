package io.github.vxmqmqtt.vxmq.protocol.model;

import java.util.List;

/**
 * Broker-facing view of one UNSUBSCRIBE packet.
 */
public record UnsubscribeRequest(
        List<String> topicFilters,
        UnsubscribeProperties properties) {

    public UnsubscribeRequest(List<String> topicFilters) {
        this(topicFilters, UnsubscribeProperties.empty());
    }

    public UnsubscribeRequest(List<String> topicFilters, MqttUserProperties userProperties) {
        this(topicFilters, new UnsubscribeProperties(userProperties));
    }

    public UnsubscribeRequest {
        topicFilters = List.copyOf(topicFilters);
        properties = properties == null ? UnsubscribeProperties.empty() : properties;
    }
}
