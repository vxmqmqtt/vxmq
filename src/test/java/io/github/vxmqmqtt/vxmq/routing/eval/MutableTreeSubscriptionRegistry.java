package io.github.vxmqmqtt.vxmq.routing.eval;

import io.github.vxmqmqtt.vxmq.routing.DefaultMqttTopicSupport;
import io.github.vxmqmqtt.vxmq.routing.MqttTopicSupport;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-only copy of the original mutable subscription tree used as an unsafe comparison baseline.
 */
final class MutableTreeSubscriptionRegistry implements SubscriptionRegistry {

    private final MutableSubscriptionTreeNode root = new MutableSubscriptionTreeNode();
    private final MqttTopicSupport mqttTopicSupport;

    MutableTreeSubscriptionRegistry() {
        this(new DefaultMqttTopicSupport());
    }

    MutableTreeSubscriptionRegistry(MqttTopicSupport mqttTopicSupport) {
        this.mqttTopicSupport = mqttTopicSupport;
    }

    @Override
    public void addSubscription(SubscriptionBinding subscriptionBinding) {
        if (!mqttTopicSupport.isValidFilter(subscriptionBinding.topicFilter())) {
            throw new IllegalArgumentException("Invalid topic filter: " + subscriptionBinding.topicFilter());
        }

        String[] levels = levels(subscriptionBinding.topicFilter());
        MutableSubscriptionTreeNode current = root;
        for (int index = 0; index < levels.length; index++) {
            String level = levels[index];
            if ("#".equals(level)) {
                current.multiLevelWildcardBindings().put(subscriptionBinding.clientId(), subscriptionBinding);
                return;
            }
            current = "+".equals(level)
                    ? current.ensureSingleLevelWildcardChild()
                    : current.exactChildren().computeIfAbsent(level, ignored -> new MutableSubscriptionTreeNode());
        }
        current.terminalBindings().put(subscriptionBinding.clientId(), subscriptionBinding);
    }

    @Override
    public boolean removeSubscription(String clientId, String topicFilter) {
        if (!mqttTopicSupport.isValidFilter(topicFilter)) {
            return false;
        }
        return remove(root, levels(topicFilter), 0, clientId);
    }

    @Override
    public Collection<SubscriptionBinding> match(String topicName) {
        if (!mqttTopicSupport.isValidTopicName(topicName)) {
            return List.of();
        }

        Map<String, SubscriptionBinding> deduplicated = new LinkedHashMap<>();
        match(root, levels(topicName), 0, deduplicated);
        return new ArrayList<>(deduplicated.values());
    }

    private void match(
            MutableSubscriptionTreeNode node,
            String[] topicLevels,
            int levelIndex,
            Map<String, SubscriptionBinding> deduplicated) {
        mergeBindings(node.multiLevelWildcardBindings(), deduplicated);
        if (levelIndex == topicLevels.length) {
            mergeBindings(node.terminalBindings(), deduplicated);
            return;
        }

        MutableSubscriptionTreeNode exactChild = node.exactChildren().get(topicLevels[levelIndex]);
        if (exactChild != null) {
            match(exactChild, topicLevels, levelIndex + 1, deduplicated);
        }
        if (node.singleLevelWildcardChild() != null) {
            match(node.singleLevelWildcardChild(), topicLevels, levelIndex + 1, deduplicated);
        }
    }

    private void mergeBindings(
            Map<String, SubscriptionBinding> bindings,
            Map<String, SubscriptionBinding> deduplicated) {
        for (SubscriptionBinding binding : bindings.values()) {
            deduplicated.merge(binding.clientId(), binding, (left, right) ->
                    left.grantedQos().value() >= right.grantedQos().value() ? left : right);
        }
    }

    private boolean remove(
            MutableSubscriptionTreeNode node,
            String[] filterLevels,
            int levelIndex,
            String clientId) {
        boolean removed;
        if (levelIndex == filterLevels.length) {
            removed = node.terminalBindings().remove(clientId) != null;
            return removed;
        }

        String level = filterLevels[levelIndex];
        if ("#".equals(level)) {
            removed = node.multiLevelWildcardBindings().remove(clientId) != null;
            return removed;
        }

        MutableSubscriptionTreeNode child = "+".equals(level)
                ? node.singleLevelWildcardChild()
                : node.exactChildren().get(level);
        if (child == null) {
            return false;
        }

        removed = remove(child, filterLevels, levelIndex + 1, clientId);
        if (!removed) {
            return false;
        }

        if ("+".equals(level)) {
            node.clearSingleLevelWildcardChildIfUnused();
        } else if (child.isEmpty()) {
            node.exactChildren().remove(level);
        }
        return true;
    }

    private String[] levels(String topic) {
        return topic.split("/", -1);
    }
}
