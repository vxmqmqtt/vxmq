package io.github.vxmqmqtt.vxmq.retained;

import io.github.vxmqmqtt.vxmq.protocol.model.PublishProperties;
import io.github.vxmqmqtt.vxmq.routing.MqttTopicSupport;
import io.netty.handler.codec.mqtt.MqttQoS;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory retained-message store for the current single-node broker milestone.
 */
@ApplicationScoped
public class InMemoryRetainedMessageRegistry implements RetainedMessageRegistry {

    // key: topicName, value: RetainedMessage
    private final Map<String, RetainedMessage> retainedMessagesByTopic = new ConcurrentHashMap<>();
    private final MqttTopicSupport mqttTopicSupport;

    public InMemoryRetainedMessageRegistry(MqttTopicSupport mqttTopicSupport) {
        this.mqttTopicSupport = mqttTopicSupport;
    }

    @Override
    public void putRetained(String topicName, byte[] payload, MqttQoS qos, PublishProperties properties) {
        retainedMessagesByTopic.put(topicName, new RetainedMessage(
                topicName,
                payload == null ? null : payload.clone(),
                qos,
                true,
                properties));
    }

    @Override
    public boolean removeRetained(String topicName) {
        return retainedMessagesByTopic.remove(topicName) != null;
    }

    @Override
    public List<RetainedMessage> findMatching(String topicFilter) {
        return retainedMessagesByTopic.entrySet()
                .stream()
                .filter(entry -> mqttTopicSupport.matches(topicFilter, entry.getKey()))
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(Map.Entry::getValue)
                .map(retainedMessage -> new RetainedMessage(
                        retainedMessage.topicName(),
                        retainedMessage.payloadCopy(),
                        retainedMessage.qos(),
                        retainedMessage.retain(),
                        retainedMessage.properties()))
                .toList();
    }

    @Override
    public Optional<RetainedMessage> findExact(String topicName) {
        RetainedMessage retainedMessage = retainedMessagesByTopic.get(topicName);
        if (retainedMessage == null) {
            return Optional.empty();
        }
        return Optional.of(new RetainedMessage(
                retainedMessage.topicName(),
                retainedMessage.payloadCopy(),
                retainedMessage.qos(),
                retainedMessage.retain(),
                retainedMessage.properties()));
    }
}
