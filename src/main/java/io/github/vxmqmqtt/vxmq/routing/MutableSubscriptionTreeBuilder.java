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
            Map<String, ImmutableSubscriptionTreeNode> immutableChildren = new HashMap<>(exactChildren.size());
            exactChildren.forEach((level, child) -> immutableChildren.put(level, child.toImmutable()));
            ImmutableSubscriptionTreeNode wildcardChild =
                    singleLevelWildcardChild == null ? null : singleLevelWildcardChild.toImmutable();
            return ImmutableSubscriptionTreeNode.create(
                    ImmutableSubscriptionChildren.from(immutableChildren),
                    wildcardChild,
                    ImmutableSubscriptionBindings.from(terminalBindings),
                    ImmutableSubscriptionBindings.from(multiLevelWildcardBindings));
        }
    }
}
