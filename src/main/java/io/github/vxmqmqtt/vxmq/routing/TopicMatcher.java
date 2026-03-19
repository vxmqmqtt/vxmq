package io.github.vxmqmqtt.vxmq.routing;

public interface TopicMatcher {

    boolean isValidFilter(String topicFilter);

    boolean isValidTopicName(String topicName);

    boolean matches(String topicFilter, String topicName);
}
