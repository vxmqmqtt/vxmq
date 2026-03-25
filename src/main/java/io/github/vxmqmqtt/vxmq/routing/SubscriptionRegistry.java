package io.github.vxmqmqtt.vxmq.routing;

import java.util.Collection;

/**
 * Stores and resolves active subscription bindings.
 */
public interface SubscriptionRegistry {

    /**
     * Adds or replaces a subscription binding.
     */
    void addSubscription(SubscriptionBinding subscriptionBinding);

    /**
     * Removes a subscription binding and reports whether anything was deleted.
     */
    boolean removeSubscription(String clientId, String topicFilter);

    /**
     * Finds the subscribers whose topic filters match the published topic name.
     */
    Collection<SubscriptionBinding> match(String topicName);
}
