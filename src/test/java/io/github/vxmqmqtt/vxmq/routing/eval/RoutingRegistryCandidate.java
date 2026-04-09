package io.github.vxmqmqtt.vxmq.routing.eval;

import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import java.util.Collection;

/**
 * Common test-facing adapter used to compare routing concurrency strategies under the same harness.
 */
interface RoutingRegistryCandidate extends AutoCloseable {

    /**
     * Returns a short human-readable label for benchmark and assertion output.
     */
    String name();

    /**
     * Adds or replaces a subscription binding.
     */
    void addSubscription(SubscriptionBinding binding);

    /**
     * Removes a subscription binding and reports whether anything was deleted.
     */
    boolean removeSubscription(String clientId, String topicFilter);

    /**
     * Resolves all matching subscription bindings for the given topic name.
     */
    Collection<SubscriptionBinding> match(String topicName);

    @Override
    default void close() throws Exception {
        // Most candidates are in-memory only and do not need explicit cleanup.
    }
}
