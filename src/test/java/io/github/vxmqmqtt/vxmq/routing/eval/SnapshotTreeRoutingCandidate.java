package io.github.vxmqmqtt.vxmq.routing.eval;

import io.github.vxmqmqtt.vxmq.routing.DefaultMqttTopicSupport;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import io.github.vxmqmqtt.vxmq.routing.MqttTopicSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Copy-on-write subscription tree candidate that keeps reads lock-free by replacing immutable snapshots.
 */
final class SnapshotTreeRoutingCandidate implements RoutingRegistryCandidate {

    private final MqttTopicSupport mqttTopicSupport = new DefaultMqttTopicSupport();
    private final AtomicReference<ImmutableNode> root = new AtomicReference<>(ImmutableNode.empty());

    @Override
    public String name() {
        return "snapshot-tree";
    }

    @Override
    public void addSubscription(SubscriptionBinding binding) {
        if (!mqttTopicSupport.isValidFilter(binding.topicFilter())) {
            throw new IllegalArgumentException("Invalid topic filter: " + binding.topicFilter());
        }

        String[] levels = levels(binding.topicFilter());
        while (true) {
            ImmutableNode current = root.get();
            ImmutableNode updated = current.add(levels, 0, binding);
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
            ImmutableNode current = root.get();
            RemoveResult removed = current.remove(levels, 0, clientId);
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

    private String[] levels(String topic) {
        return topic.split("/", -1);
    }

    /**
     * Factory for the immutable snapshot candidate.
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

    private record RemoveResult(ImmutableNode node, boolean removed) {
    }

    private static final class ImmutableNode {

        private static final ImmutableNode EMPTY = new ImmutableNode(Map.of(), null, Map.of(), Map.of());

        private final Map<String, ImmutableNode> exactChildren;
        private final ImmutableNode singleLevelWildcardChild;
        private final Map<String, SubscriptionBinding> terminalBindings;
        private final Map<String, SubscriptionBinding> multiLevelWildcardBindings;

        private ImmutableNode(
                Map<String, ImmutableNode> exactChildren,
                ImmutableNode singleLevelWildcardChild,
                Map<String, SubscriptionBinding> terminalBindings,
                Map<String, SubscriptionBinding> multiLevelWildcardBindings) {
            this.exactChildren = exactChildren;
            this.singleLevelWildcardChild = singleLevelWildcardChild;
            this.terminalBindings = terminalBindings;
            this.multiLevelWildcardBindings = multiLevelWildcardBindings;
        }

        static ImmutableNode empty() {
            return EMPTY;
        }

        ImmutableNode add(String[] filterLevels, int levelIndex, SubscriptionBinding binding) {
            if (levelIndex == filterLevels.length) {
                Map<String, SubscriptionBinding> updatedTerminalBindings = mutableCopy(terminalBindings);
                updatedTerminalBindings.put(binding.clientId(), binding);
                return new ImmutableNode(
                        exactChildren,
                        singleLevelWildcardChild,
                        immutableCopy(updatedTerminalBindings),
                        multiLevelWildcardBindings);
            }

            String level = filterLevels[levelIndex];
            if ("#".equals(level)) {
                Map<String, SubscriptionBinding> updatedMultiLevelWildcardBindings = mutableCopy(multiLevelWildcardBindings);
                updatedMultiLevelWildcardBindings.put(binding.clientId(), binding);
                return new ImmutableNode(
                        exactChildren,
                        singleLevelWildcardChild,
                        terminalBindings,
                        immutableCopy(updatedMultiLevelWildcardBindings));
            }

            if ("+".equals(level)) {
                ImmutableNode currentWildcardChild = singleLevelWildcardChild == null ? empty() : singleLevelWildcardChild;
                ImmutableNode updatedWildcardChild = currentWildcardChild.add(filterLevels, levelIndex + 1, binding);
                return new ImmutableNode(
                        exactChildren,
                        updatedWildcardChild,
                        terminalBindings,
                        multiLevelWildcardBindings);
            }

            Map<String, ImmutableNode> updatedChildren = new LinkedHashMap<>(exactChildren);
            ImmutableNode currentChild = updatedChildren.getOrDefault(level, empty());
            updatedChildren.put(level, currentChild.add(filterLevels, levelIndex + 1, binding));
            return new ImmutableNode(
                    immutableCopy(updatedChildren),
                    singleLevelWildcardChild,
                    terminalBindings,
                    multiLevelWildcardBindings);
        }

        RemoveResult remove(String[] filterLevels, int levelIndex, String clientId) {
            if (levelIndex == filterLevels.length) {
                if (!terminalBindings.containsKey(clientId)) {
                    return new RemoveResult(this, false);
                }
                Map<String, SubscriptionBinding> updatedTerminalBindings = mutableCopy(terminalBindings);
                updatedTerminalBindings.remove(clientId);
                return new RemoveResult(prune(
                        exactChildren,
                        singleLevelWildcardChild,
                        immutableCopy(updatedTerminalBindings),
                        multiLevelWildcardBindings), true);
            }

            String level = filterLevels[levelIndex];
            if ("#".equals(level)) {
                if (!multiLevelWildcardBindings.containsKey(clientId)) {
                    return new RemoveResult(this, false);
                }
                Map<String, SubscriptionBinding> updatedMultiLevelWildcardBindings = mutableCopy(multiLevelWildcardBindings);
                updatedMultiLevelWildcardBindings.remove(clientId);
                return new RemoveResult(prune(
                        exactChildren,
                        singleLevelWildcardChild,
                        terminalBindings,
                        immutableCopy(updatedMultiLevelWildcardBindings)), true);
            }

            if ("+".equals(level)) {
                if (singleLevelWildcardChild == null) {
                    return new RemoveResult(this, false);
                }
                RemoveResult childResult = singleLevelWildcardChild.remove(filterLevels, levelIndex + 1, clientId);
                if (!childResult.removed()) {
                    return new RemoveResult(this, false);
                }
                ImmutableNode updatedWildcardChild = childResult.node().isEmpty() ? null : childResult.node();
                return new RemoveResult(prune(
                        exactChildren,
                        updatedWildcardChild,
                        terminalBindings,
                        multiLevelWildcardBindings), true);
            }

            ImmutableNode child = exactChildren.get(level);
            if (child == null) {
                return new RemoveResult(this, false);
            }

            RemoveResult childResult = child.remove(filterLevels, levelIndex + 1, clientId);
            if (!childResult.removed()) {
                return new RemoveResult(this, false);
            }

            Map<String, ImmutableNode> updatedChildren = new LinkedHashMap<>(exactChildren);
            if (childResult.node().isEmpty()) {
                updatedChildren.remove(level);
            } else {
                updatedChildren.put(level, childResult.node());
            }
            return new RemoveResult(prune(
                    immutableCopy(updatedChildren),
                    singleLevelWildcardChild,
                    terminalBindings,
                    multiLevelWildcardBindings), true);
        }

        void match(String[] topicLevels, int levelIndex, Map<String, SubscriptionBinding> deduplicated) {
            mergeBindings(multiLevelWildcardBindings, deduplicated);
            if (levelIndex == topicLevels.length) {
                mergeBindings(terminalBindings, deduplicated);
                return;
            }

            ImmutableNode exactChild = exactChildren.get(topicLevels[levelIndex]);
            if (exactChild != null) {
                exactChild.match(topicLevels, levelIndex + 1, deduplicated);
            }
            if (singleLevelWildcardChild != null) {
                singleLevelWildcardChild.match(topicLevels, levelIndex + 1, deduplicated);
            }
        }

        boolean isEmpty() {
            return exactChildren.isEmpty()
                    && singleLevelWildcardChild == null
                    && terminalBindings.isEmpty()
                    && multiLevelWildcardBindings.isEmpty();
        }

        private ImmutableNode prune(
                Map<String, ImmutableNode> exactChildren,
                ImmutableNode singleLevelWildcardChild,
                Map<String, SubscriptionBinding> terminalBindings,
                Map<String, SubscriptionBinding> multiLevelWildcardBindings) {
            ImmutableNode candidate = new ImmutableNode(
                    exactChildren,
                    singleLevelWildcardChild,
                    terminalBindings,
                    multiLevelWildcardBindings);
            return candidate.isEmpty() ? empty() : candidate;
        }

        private void mergeBindings(
                Map<String, SubscriptionBinding> bindings,
                Map<String, SubscriptionBinding> deduplicated) {
            for (SubscriptionBinding binding : bindings.values()) {
                deduplicated.merge(binding.clientId(), binding, (left, right) ->
                        left.grantedQos().value() >= right.grantedQos().value() ? left : right);
            }
        }

        private <T> Map<String, T> mutableCopy(Map<String, T> source) {
            return new LinkedHashMap<>(source);
        }

        private <T> Map<String, T> immutableCopy(Map<String, T> source) {
            return source.isEmpty() ? Map.of() : Map.copyOf(source);
        }
    }
}
