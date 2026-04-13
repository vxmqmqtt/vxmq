package io.github.vxmqmqtt.vxmq.routing;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Mutable builder used to construct a full immutable routing snapshot in one publication step.
 */
final class MutableSubscriptionTreeBuilder {

    private final MqttTopicSupport mqttTopicSupport;
    private final MutableNode root = new MutableNode();

    MutableSubscriptionTreeBuilder(MqttTopicSupport mqttTopicSupport) {
        this.mqttTopicSupport = mqttTopicSupport;
    }

    void addAll(Collection<SubscriptionBinding> bindings) {
        for (SubscriptionBinding binding : bindings) {
            add(binding);
        }
    }

    ImmutableSubscriptionTreeNode build() {
        return root.toImmutable();
    }

    private void add(SubscriptionBinding binding) {
        if (!mqttTopicSupport.isValidFilter(binding.topicFilter())) {
            throw new IllegalArgumentException("Invalid topic filter: " + binding.topicFilter());
        }
        root.add(binding.topicFilter().split("/", -1), 0, binding);
    }

    private static final class MutableNode {

        private final Map<String, MutableNode> exactChildren = new HashMap<>();
        private MutableNode singleLevelWildcardChild;
        private final Map<String, SubscriptionBinding> terminalBindings = new HashMap<>();
        private final Map<String, SubscriptionBinding> multiLevelWildcardBindings = new HashMap<>();

        private void add(String[] filterLevels, int levelIndex, SubscriptionBinding binding) {
            if (levelIndex == filterLevels.length) {
                terminalBindings.put(binding.clientId(), binding);
                return;
            }

            String level = filterLevels[levelIndex];
            if ("#".equals(level)) {
                multiLevelWildcardBindings.put(binding.clientId(), binding);
                return;
            }

            if ("+".equals(level)) {
                if (singleLevelWildcardChild == null) {
                    singleLevelWildcardChild = new MutableNode();
                }
                singleLevelWildcardChild.add(filterLevels, levelIndex + 1, binding);
                return;
            }

            exactChildren
                    .computeIfAbsent(level, ignored -> new MutableNode())
                    .add(filterLevels, levelIndex + 1, binding);
        }

        private ImmutableSubscriptionTreeNode toImmutable() {
            ImmutableSubscriptionChildren immutableChildren = toImmutableChildren();
            ImmutableSubscriptionTreeNode wildcardChild =
                    singleLevelWildcardChild == null ? null : singleLevelWildcardChild.toImmutable();
            return ImmutableSubscriptionTreeNode.create(
                    immutableChildren,
                    wildcardChild,
                    ImmutableSubscriptionBindings.from(terminalBindings),
                    ImmutableSubscriptionBindings.from(multiLevelWildcardBindings));
        }

        private ImmutableSubscriptionChildren toImmutableChildren() {
            if (exactChildren.isEmpty()) {
                return ImmutableSubscriptionChildren.empty();
            }
            if (exactChildren.size() == 1) {
                Map.Entry<String, MutableNode> entry = exactChildren.entrySet().iterator().next();
                return ImmutableSubscriptionChildren.from(Map.of(entry.getKey(), entry.getValue().toImmutable()));
            }
            if (exactChildren.size() <= 4) {
                String[] levels = new String[exactChildren.size()];
                ImmutableSubscriptionTreeNode[] children = new ImmutableSubscriptionTreeNode[exactChildren.size()];
                int index = 0;
                for (Map.Entry<String, MutableNode> entry : exactChildren.entrySet()) {
                    levels[index] = entry.getKey();
                    children[index] = entry.getValue().toImmutable();
                    index++;
                }
                return ImmutableSubscriptionChildren.fromSmallSnapshot(levels, children);
            }

            Object[] buckets = new Object[64];
            exactChildren.forEach((level, child) -> {
                int bucketIndex = level.hashCode() & 63;
                @SuppressWarnings("unchecked")
                Map<String, ImmutableSubscriptionTreeNode> bucket =
                        buckets[bucketIndex] == null ? new HashMap<>() : (Map<String, ImmutableSubscriptionTreeNode>) buckets[bucketIndex];
                bucket.put(level, child.toImmutable());
                buckets[bucketIndex] = bucket;
            });
            return ImmutableSubscriptionChildren.fromBucketSnapshot(buckets, exactChildren.size());
        }
    }
}
