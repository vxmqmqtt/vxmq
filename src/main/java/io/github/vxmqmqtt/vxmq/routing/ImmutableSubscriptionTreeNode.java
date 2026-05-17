package io.github.vxmqmqtt.vxmq.routing;

import java.util.Map;

/**
 * Immutable subscription tree node used by the production copy-on-write routing index.
 */
final class ImmutableSubscriptionTreeNode {

    private static final ImmutableSubscriptionTreeNode EMPTY = new ImmutableSubscriptionTreeNode(
            ImmutableSubscriptionChildren.empty(),
            null,
            ImmutableSubscriptionBindings.empty(),
            ImmutableSubscriptionBindings.empty());

    private final ImmutableSubscriptionChildren exactChildren;
    private final ImmutableSubscriptionTreeNode singleLevelWildcardChild;
    private final ImmutableSubscriptionBindings terminalBindings;
    private final ImmutableSubscriptionBindings multiLevelWildcardBindings;

    private ImmutableSubscriptionTreeNode(
            ImmutableSubscriptionChildren exactChildren,
            ImmutableSubscriptionTreeNode singleLevelWildcardChild,
            ImmutableSubscriptionBindings terminalBindings,
            ImmutableSubscriptionBindings multiLevelWildcardBindings) {
        this.exactChildren = exactChildren;
        this.singleLevelWildcardChild = singleLevelWildcardChild;
        this.terminalBindings = terminalBindings;
        this.multiLevelWildcardBindings = multiLevelWildcardBindings;
    }

    static ImmutableSubscriptionTreeNode empty() {
        return EMPTY;
    }

    static ImmutableSubscriptionTreeNode create(
            ImmutableSubscriptionChildren exactChildren,
            ImmutableSubscriptionTreeNode singleLevelWildcardChild,
            ImmutableSubscriptionBindings terminalBindings,
            ImmutableSubscriptionBindings multiLevelWildcardBindings) {
        if (exactChildren.isEmpty()
                && singleLevelWildcardChild == null
                && terminalBindings.isEmpty()
                && multiLevelWildcardBindings.isEmpty()) {
            return empty();
        }
        return new ImmutableSubscriptionTreeNode(
                exactChildren,
                singleLevelWildcardChild,
                terminalBindings,
                multiLevelWildcardBindings);
    }

    ImmutableSubscriptionTreeNode add(String[] filterLevels, int levelIndex, SubscriptionBinding binding) {
        if (levelIndex == filterLevels.length) {
            ImmutableSubscriptionBindings updatedTerminalBindings = terminalBindings.put(binding);
            return updatedTerminalBindings == terminalBindings
                    ? this
                    : create(exactChildren, singleLevelWildcardChild, updatedTerminalBindings, multiLevelWildcardBindings);
        }

        String level = filterLevels[levelIndex];
        if ("#".equals(level)) {
            ImmutableSubscriptionBindings updatedMultiLevelBindings = multiLevelWildcardBindings.put(binding);
            return updatedMultiLevelBindings == multiLevelWildcardBindings
                    ? this
                    : create(exactChildren, singleLevelWildcardChild, terminalBindings, updatedMultiLevelBindings);
        }

        if ("+".equals(level)) {
            ImmutableSubscriptionTreeNode currentWildcardChild =
                    singleLevelWildcardChild == null ? empty() : singleLevelWildcardChild;
            ImmutableSubscriptionTreeNode updatedWildcardChild =
                    currentWildcardChild.add(filterLevels, levelIndex + 1, binding);
            return updatedWildcardChild == currentWildcardChild
                    ? this
                    : create(exactChildren, updatedWildcardChild, terminalBindings, multiLevelWildcardBindings);
        }

        ImmutableSubscriptionTreeNode currentChild = exactChildren.get(level);
        if (currentChild == null) {
            currentChild = empty();
        }
        ImmutableSubscriptionTreeNode updatedChild = currentChild.add(filterLevels, levelIndex + 1, binding);
        if (updatedChild == currentChild) {
            return this;
        }
        return create(
                exactChildren.put(level, updatedChild),
                singleLevelWildcardChild,
                terminalBindings,
                multiLevelWildcardBindings);
    }

    RemoveResult remove(String[] filterLevels, int levelIndex, String clientId) {
        if (levelIndex == filterLevels.length) {
            ImmutableSubscriptionBindings.RemoveResult removedTerminalBindings = terminalBindings.remove(clientId);
            return removedTerminalBindings.removed()
                    ? new RemoveResult(create(
                    exactChildren,
                    singleLevelWildcardChild,
                    removedTerminalBindings.bindings(),
                    multiLevelWildcardBindings), true)
                    : new RemoveResult(this, false);
        }

        String level = filterLevels[levelIndex];
        if ("#".equals(level)) {
            ImmutableSubscriptionBindings.RemoveResult removedMultiLevelBindings = multiLevelWildcardBindings.remove(clientId);
            return removedMultiLevelBindings.removed()
                    ? new RemoveResult(create(
                    exactChildren,
                    singleLevelWildcardChild,
                    terminalBindings,
                    removedMultiLevelBindings.bindings()), true)
                    : new RemoveResult(this, false);
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
                    create(
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

        ImmutableSubscriptionChildren updatedChildren = childResult.node().isEmpty()
                ? exactChildren.remove(level).children()
                : exactChildren.put(level, childResult.node());
        return new RemoveResult(
                create(
                        updatedChildren,
                        singleLevelWildcardChild,
                        terminalBindings,
                        multiLevelWildcardBindings),
                true);
    }

    void match(String[] topicLevels, int levelIndex, Map<String, SubscriptionBinding> deduplicated) {
        boolean atSystemTopicRoot = levelIndex == 0 && topicLevels.length > 0 && topicLevels[0].startsWith("$");
        if (!atSystemTopicRoot) {
            multiLevelWildcardBindings.mergeInto(deduplicated);
        }
        if (levelIndex == topicLevels.length) {
            terminalBindings.mergeInto(deduplicated);
            return;
        }

        ImmutableSubscriptionTreeNode exactChild = exactChildren.get(topicLevels[levelIndex]);
        if (exactChild != null) {
            exactChild.match(topicLevels, levelIndex + 1, deduplicated);
        }
        if (!atSystemTopicRoot && singleLevelWildcardChild != null) {
            singleLevelWildcardChild.match(topicLevels, levelIndex + 1, deduplicated);
        }
    }

    int nodeCount() {
        int count = 1;
        int[] childCount = new int[1];
        exactChildren.forEachChild(child -> childCount[0] += child.nodeCount());
        if (singleLevelWildcardChild != null) {
            count += singleLevelWildcardChild.nodeCount();
        }
        return count + childCount[0];
    }

    boolean isEmpty() {
        return exactChildren.isEmpty()
                && singleLevelWildcardChild == null
                && terminalBindings.isEmpty()
                && multiLevelWildcardBindings.isEmpty();
    }
    record RemoveResult(ImmutableSubscriptionTreeNode node, boolean removed) {
    }
}
