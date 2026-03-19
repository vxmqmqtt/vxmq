package io.github.vxmqmqtt.vxmq.routing;

/**
 * Validates and matches MQTT topic names and topic filters.
 */
public interface TopicMatcher {

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
