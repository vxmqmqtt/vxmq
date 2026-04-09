package io.github.vxmqmqtt.vxmq.routing;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable subscription tree node used by the production copy-on-write routing index.
 */
final class ImmutableSubscriptionTreeNode {

    private static final ImmutableSubscriptionTreeNode EMPTY =
            new ImmutableSubscriptionTreeNode(Map.of(), null, Map.of(), Map.of());

    private final Map<String, ImmutableSubscriptionTreeNode> exactChildren;
    private final ImmutableSubscriptionTreeNode singleLevelWildcardChild;
    private final Map<String, SubscriptionBinding> terminalBindings;
    private final Map<String, SubscriptionBinding> multiLevelWildcardBindings;

    private ImmutableSubscriptionTreeNode(
            Map<String, ImmutableSubscriptionTreeNode> exactChildren,
            ImmutableSubscriptionTreeNode singleLevelWildcardChild,
            Map<String, SubscriptionBinding> terminalBindings,
            Map<String, SubscriptionBinding> multiLevelWildcardBindings) {
        this.exactChildren = exactChildren;
        this.singleLevelWildcardChild = singleLevelWildcardChild;
        this.terminalBindings = terminalBindings;
        this.multiLevelWildcardBindings = multiLevelWildcardBindings;
    }

    static ImmutableSubscriptionTreeNode empty() {
        return EMPTY;
    }

    ImmutableSubscriptionTreeNode add(String[] filterLevels, int levelIndex, SubscriptionBinding binding) {
        if (levelIndex == filterLevels.length) {
            Map<String, SubscriptionBinding> updatedTerminalBindings = mutableBindingsCopy(terminalBindings);
            updatedTerminalBindings.put(binding.clientId(), binding);
            return new ImmutableSubscriptionTreeNode(
                    exactChildren,
                    singleLevelWildcardChild,
                    immutableBindingsCopy(updatedTerminalBindings),
                    multiLevelWildcardBindings);
        }

        String level = filterLevels[levelIndex];
        if ("#".equals(level)) {
            Map<String, SubscriptionBinding> updatedMultiLevelBindings = mutableBindingsCopy(multiLevelWildcardBindings);
            updatedMultiLevelBindings.put(binding.clientId(), binding);
            return new ImmutableSubscriptionTreeNode(
                    exactChildren,
                    singleLevelWildcardChild,
                    terminalBindings,
                    immutableBindingsCopy(updatedMultiLevelBindings));
        }

        if ("+".equals(level)) {
            ImmutableSubscriptionTreeNode currentWildcardChild =
                    singleLevelWildcardChild == null ? empty() : singleLevelWildcardChild;
            ImmutableSubscriptionTreeNode updatedWildcardChild =
                    currentWildcardChild.add(filterLevels, levelIndex + 1, binding);
            return new ImmutableSubscriptionTreeNode(
                    exactChildren,
                    updatedWildcardChild,
                    terminalBindings,
                    multiLevelWildcardBindings);
        }

        Map<String, ImmutableSubscriptionTreeNode> updatedChildren = new LinkedHashMap<>(exactChildren);
        ImmutableSubscriptionTreeNode currentChild = updatedChildren.getOrDefault(level, empty());
        updatedChildren.put(level, currentChild.add(filterLevels, levelIndex + 1, binding));
        return new ImmutableSubscriptionTreeNode(
                immutableChildCopy(updatedChildren),
                singleLevelWildcardChild,
                terminalBindings,
                multiLevelWildcardBindings);
    }

    RemoveResult remove(String[] filterLevels, int levelIndex, String clientId) {
        if (levelIndex == filterLevels.length) {
            if (!terminalBindings.containsKey(clientId)) {
                return new RemoveResult(this, false);
            }
            Map<String, SubscriptionBinding> updatedTerminalBindings = mutableBindingsCopy(terminalBindings);
            updatedTerminalBindings.remove(clientId);
            return new RemoveResult(
                    prune(
                            exactChildren,
                            singleLevelWildcardChild,
                            immutableBindingsCopy(updatedTerminalBindings),
                            multiLevelWildcardBindings),
                    true);
        }

        String level = filterLevels[levelIndex];
        if ("#".equals(level)) {
            if (!multiLevelWildcardBindings.containsKey(clientId)) {
                return new RemoveResult(this, false);
            }
            Map<String, SubscriptionBinding> updatedMultiLevelBindings = mutableBindingsCopy(multiLevelWildcardBindings);
            updatedMultiLevelBindings.remove(clientId);
            return new RemoveResult(
                    prune(
                            exactChildren,
                            singleLevelWildcardChild,
                            terminalBindings,
                            immutableBindingsCopy(updatedMultiLevelBindings)),
                    true);
        }

        if ("+".equals(level)) {
            if (singleLevelWildcardChild == null) {
                return new RemoveResult(this, false);
            }
            RemoveResult childResult = singleLevelWildcardChild.remove(filterLevels, levelIndex + 1, clientId);
            if (!childResult.removed()) {
                return new RemoveResult(this, false);
            }
            ImmutableSubscriptionTreeNode updatedWildcardChild =
                    childResult.node().isEmpty() ? null : childResult.node();
            return new RemoveResult(
                    prune(
                            exactChildren,
                            updatedWildcardChild,
                            terminalBindings,
                            multiLevelWildcardBindings),
                    true);
        }

        ImmutableSubscriptionTreeNode child = exactChildren.get(level);
        if (child == null) {
            return new RemoveResult(this, false);
        }

        RemoveResult childResult = child.remove(filterLevels, levelIndex + 1, clientId);
        if (!childResult.removed()) {
            return new RemoveResult(this, false);
        }

        Map<String, ImmutableSubscriptionTreeNode> updatedChildren = new LinkedHashMap<>(exactChildren);
        if (childResult.node().isEmpty()) {
            updatedChildren.remove(level);
        } else {
            updatedChildren.put(level, childResult.node());
        }
        return new RemoveResult(
                prune(
                        immutableChildCopy(updatedChildren),
                        singleLevelWildcardChild,
                        terminalBindings,
                        multiLevelWildcardBindings),
                true);
    }

    void match(String[] topicLevels, int levelIndex, Map<String, SubscriptionBinding> deduplicated) {
        mergeBindings(multiLevelWildcardBindings, deduplicated);
        if (levelIndex == topicLevels.length) {
            mergeBindings(terminalBindings, deduplicated);
            return;
        }

        ImmutableSubscriptionTreeNode exactChild = exactChildren.get(topicLevels[levelIndex]);
        if (exactChild != null) {
            exactChild.match(topicLevels, levelIndex + 1, deduplicated);
        }
        if (singleLevelWildcardChild != null) {
            singleLevelWildcardChild.match(topicLevels, levelIndex + 1, deduplicated);
        }
    }

    int nodeCount() {
        int count = 1;
        for (ImmutableSubscriptionTreeNode child : exactChildren.values()) {
            count += child.nodeCount();
        }
        if (singleLevelWildcardChild != null) {
            count += singleLevelWildcardChild.nodeCount();
        }
        return count;
    }

    boolean isEmpty() {
        return exactChildren.isEmpty()
                && singleLevelWildcardChild == null
                && terminalBindings.isEmpty()
                && multiLevelWildcardBindings.isEmpty();
    }

    private ImmutableSubscriptionTreeNode prune(
            Map<String, ImmutableSubscriptionTreeNode> exactChildren,
            ImmutableSubscriptionTreeNode singleLevelWildcardChild,
            Map<String, SubscriptionBinding> terminalBindings,
            Map<String, SubscriptionBinding> multiLevelWildcardBindings) {
        ImmutableSubscriptionTreeNode candidate = new ImmutableSubscriptionTreeNode(
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

    private Map<String, SubscriptionBinding> mutableBindingsCopy(Map<String, SubscriptionBinding> source) {
        return new LinkedHashMap<>(source);
    }

    private Map<String, SubscriptionBinding> immutableBindingsCopy(Map<String, SubscriptionBinding> source) {
        return source.isEmpty() ? Map.of() : Map.copyOf(source);
    }

    private Map<String, ImmutableSubscriptionTreeNode> immutableChildCopy(Map<String, ImmutableSubscriptionTreeNode> source) {
        return source.isEmpty() ? Map.of() : Map.copyOf(source);
    }

    record RemoveResult(ImmutableSubscriptionTreeNode node, boolean removed) {
    }
}
