package io.github.vxmqmqtt.vxmq.routing;

/**
 * Provides reusable MQTT topic-name and topic-filter rules for validation and helper matching.
 */
public interface MqttTopicSupport {

    /**
     * Returns whether the supplied topic filter is valid for subscriptions.
     */
    boolean isValidFilter(String topicFilter);

    /**
     * Returns whether the supplied topic name is valid for publishes.
     */
    boolean isValidTopicName(String topicName);

    /**
     * Returns whether a topic name matches the given topic filter.
     */
    boolean matches(String topicFilter, String topicName);
}
