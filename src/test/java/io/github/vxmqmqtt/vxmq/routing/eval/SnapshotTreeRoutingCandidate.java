package io.github.vxmqmqtt.vxmq.routing.eval;

import io.github.vxmqmqtt.vxmq.routing.DefaultMqttTopicSupport;
import io.github.vxmqmqtt.vxmq.routing.InMemorySubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Test-facing adapter that benchmarks the current production snapshot-tree implementation.
 */
final class SnapshotTreeRoutingCandidate implements RoutingRegistryCandidate {

    private final InMemorySubscriptionRegistry delegate =
            new InMemorySubscriptionRegistry(new DefaultMqttTopicSupport());

    @Override
    public String name() {
        return "snapshot-tree";
    }

    @Override
    public void addSubscription(SubscriptionBinding binding) {
        delegate.addSubscription(binding);
    }

    @Override
    public boolean removeSubscription(String clientId, String topicFilter) {
        return delegate.removeSubscription(clientId, topicFilter);
    }

    @Override
    public Collection<SubscriptionBinding> match(String topicName) {
        return new ArrayList<>(delegate.match(topicName));
    }

    /**
     * Factory for the production snapshot-tree candidate.
     */
    static final class Factory implements RoutingRegistryCandidateFactory {

        @Override
        public String name() {
            return "snapshot-tree";
        }

        @Override
        public RoutingRegistryCandidate create() {
            return new SnapshotTreeRoutingCandidate();
        }
    }
}
