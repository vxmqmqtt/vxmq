package io.github.vxmqmqtt.vxmq.routing.eval;

import io.github.vxmqmqtt.vxmq.routing.MqttTopicSupport;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only baseline that preserves the pre-subscription-tree linear scan behavior.
 */
final class LinearScanSubscriptionRegistry implements SubscriptionRegistry {

    private final Map<String, Set<SubscriptionBinding>> subscriptionsByFilter = new ConcurrentHashMap<>();
    private final MqttTopicSupport mqttTopicSupport;

    LinearScanSubscriptionRegistry(MqttTopicSupport mqttTopicSupport) {
        this.mqttTopicSupport = mqttTopicSupport;
    }

    @Override
    public void addSubscription(SubscriptionBinding subscriptionBinding) {
        subscriptionsByFilter
                .computeIfAbsent(subscriptionBinding.topicFilter(), ignored -> ConcurrentHashMap.newKeySet())
                .removeIf(binding -> binding.clientId().equals(subscriptionBinding.clientId()));
        subscriptionsByFilter
                .computeIfAbsent(subscriptionBinding.topicFilter(), ignored -> ConcurrentHashMap.newKeySet())
                .add(subscriptionBinding);
    }

    @Override
    public boolean removeSubscription(String clientId, String topicFilter) {
        Set<SubscriptionBinding> bindings = subscriptionsByFilter.get(topicFilter);
        if (bindings == null) {
            return false;
        }
        boolean removed = bindings.removeIf(binding -> binding.clientId().equals(clientId));
        if (bindings.isEmpty()) {
            subscriptionsByFilter.remove(topicFilter);
        }
        return removed;
    }

    @Override
    public Collection<SubscriptionBinding> match(String topicName) {
        Map<String, SubscriptionBinding> deduplicated = new LinkedHashMap<>();
        for (Map.Entry<String, Set<SubscriptionBinding>> entry : subscriptionsByFilter.entrySet()) {
            if (!mqttTopicSupport.matches(entry.getKey(), topicName)) {
                continue;
            }
            for (SubscriptionBinding binding : entry.getValue()) {
                deduplicated.merge(binding.clientId(), binding, (left, right) ->
                        left.grantedQos().value() >= right.grantedQos().value() ? left : right);
            }
        }
        return new ArrayList<>(deduplicated.values());
    }
}
