package io.github.vxmqmqtt.vxmq.retained;

import io.github.vxmqmqtt.vxmq.protocol.model.PublishProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.List;
import java.util.Optional;

/**
 * Stores retained messages independently from live subscriptions and session state.
 */
public interface RetainedMessageRegistry {

    /**
     * Stores or replaces the retained message for the supplied topic name.
     */
    default void putRetained(String topicName, byte[] payload, MqttQoS qos) {
        putRetained(topicName, payload, qos, PublishProperties.empty());
    }

    /**
     * Stores or replaces the retained message with MQTT 5 PUBLISH properties.
     */
    void putRetained(String topicName, byte[] payload, MqttQoS qos, PublishProperties properties);

    /**
     * Removes the retained message for the supplied topic name, if any.
     */
    boolean removeRetained(String topicName);

    /**
     * Finds all retained messages whose topic names match the supplied topic filter.
     */
    List<RetainedMessage> findMatching(String topicFilter);

    /**
     * Returns the retained message for the exact topic name, if present.
     */
    Optional<RetainedMessage> findExact(String topicName);
}
