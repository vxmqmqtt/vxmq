package io.github.vxmqmqtt.vxmq.routing;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InMemorySubscriptionRegistry implements SubscriptionRegistry {

    private final Map<String, Set<SubscriptionBinding>> subscriptionsByFilter = new ConcurrentHashMap<>();
    private final TopicMatcher topicMatcher;

    public InMemorySubscriptionRegistry(TopicMatcher topicMatcher) {
        this.topicMatcher = topicMatcher;
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
            if (!topicMatcher.matches(entry.getKey(), topicName)) {
                continue;
            }
            for (SubscriptionBinding binding : entry.getValue()) {
                deduplicated.merge(binding.clientId(), binding, (left, right) ->
                        left.requestedQos() >= right.requestedQos() ? left : right);
            }
        }
        return new ArrayList<>(deduplicated.values());
    }
}
