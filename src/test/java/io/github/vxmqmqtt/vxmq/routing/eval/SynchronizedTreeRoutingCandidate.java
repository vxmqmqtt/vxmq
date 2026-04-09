package io.github.vxmqmqtt.vxmq.routing.eval;

import io.github.vxmqmqtt.vxmq.routing.DefaultMqttTopicSupport;
import io.github.vxmqmqtt.vxmq.routing.InMemorySubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Conservative baseline that keeps the current mutable tree but serializes every access with a monitor.
 */
final class SynchronizedTreeRoutingCandidate implements RoutingRegistryCandidate {

    private final InMemorySubscriptionRegistry delegate = new InMemorySubscriptionRegistry(new DefaultMqttTopicSupport());

    @Override
    public String name() {
        return "synchronized-tree";
    }

    @Override
    public synchronized void addSubscription(SubscriptionBinding binding) {
        delegate.addSubscription(binding);
    }

    @Override
    public synchronized boolean removeSubscription(String clientId, String topicFilter) {
        return delegate.removeSubscription(clientId, topicFilter);
    }

    @Override
    public synchronized Collection<SubscriptionBinding> match(String topicName) {
        return new ArrayList<>(delegate.match(topicName));
    }

    /**
     * Factory for the synchronized baseline candidate.
     */
    static final class Factory implements RoutingRegistryCandidateFactory {

        @Override
        public String name() {
            return "synchronized-tree";
        }

        @Override
        public RoutingRegistryCandidate create() {
            return new SynchronizedTreeRoutingCandidate();
        }
    }
}
