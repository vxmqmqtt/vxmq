package io.github.vxmqmqtt.vxmq.routing;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory subscription index backed by a copy-on-write subscription tree snapshot.
 */
@ApplicationScoped
public class InMemorySubscriptionRegistry implements SubscriptionRegistry {

    private final AtomicReference<ImmutableSubscriptionTreeNode> root =
            new AtomicReference<>(ImmutableSubscriptionTreeNode.empty());
    private final MqttTopicSupport mqttTopicSupport;

    public InMemorySubscriptionRegistry(MqttTopicSupport mqttTopicSupport) {
        this.mqttTopicSupport = mqttTopicSupport;
    }

    @Override
    public void addSubscription(SubscriptionBinding subscriptionBinding) {
        if (!mqttTopicSupport.isValidFilter(subscriptionBinding.topicFilter())) {
            throw new IllegalArgumentException("Invalid topic filter: " + subscriptionBinding.topicFilter());
        }

        String[] levels = levels(subscriptionBinding.topicFilter());
        while (true) {
            ImmutableSubscriptionTreeNode current = root.get();
            ImmutableSubscriptionTreeNode updated = current.add(levels, 0, subscriptionBinding);
            if (root.compareAndSet(current, updated)) {
                return;
            }
        }
    }

    @Override
    public boolean removeSubscription(String clientId, String topicFilter) {
        if (!mqttTopicSupport.isValidFilter(topicFilter)) {
            return false;
        }

        String[] levels = levels(topicFilter);
        while (true) {
            ImmutableSubscriptionTreeNode current = root.get();
            ImmutableSubscriptionTreeNode.RemoveResult removed = current.remove(levels, 0, clientId);
            if (!removed.removed()) {
                return false;
            }
            if (root.compareAndSet(current, removed.node())) {
                return true;
            }
        }
    }

    @Override
    public Collection<SubscriptionBinding> match(String topicName) {
        if (!mqttTopicSupport.isValidTopicName(topicName)) {
            return List.of();
        }

        Map<String, SubscriptionBinding> deduplicated = new LinkedHashMap<>();
        root.get().match(levels(topicName), 0, deduplicated);
        return new ArrayList<>(deduplicated.values());
    }

    /**
     * Exposes the current tree node count for pruning-oriented tests.
     */
    int nodeCount() {
        return root.get().nodeCount();
    }

    private String[] levels(String topic) {
        return topic.split("/", -1);
    }
}
