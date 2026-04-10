package io.github.vxmqmqtt.vxmq.routing;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Compact immutable child-node container optimized for the common empty and single-child cases.
 */
final class ImmutableSubscriptionChildren {

    private static final ImmutableSubscriptionChildren EMPTY =
            new ImmutableSubscriptionChildren(0, null, null, null);

    private final int size;
    private final String singleLevel;
    private final ImmutableSubscriptionTreeNode singleChild;
    private final Map<String, ImmutableSubscriptionTreeNode> manyChildren;

    private ImmutableSubscriptionChildren(
            int size,
            String singleLevel,
            ImmutableSubscriptionTreeNode singleChild,
            Map<String, ImmutableSubscriptionTreeNode> manyChildren) {
        this.size = size;
        this.singleLevel = singleLevel;
        this.singleChild = singleChild;
        this.manyChildren = manyChildren;
    }

    static ImmutableSubscriptionChildren empty() {
        return EMPTY;
    }

    static ImmutableSubscriptionChildren from(Map<String, ImmutableSubscriptionTreeNode> children) {
        if (children.isEmpty()) {
            return empty();
        }
        if (children.size() == 1) {
            Map.Entry<String, ImmutableSubscriptionTreeNode> entry = children.entrySet().iterator().next();
            return singleton(entry.getKey(), entry.getValue());
        }
        return new ImmutableSubscriptionChildren(children.size(), null, null, new HashMap<>(children));
    }

    boolean isEmpty() {
        return size == 0;
    }

    ImmutableSubscriptionTreeNode get(String level) {
        return switch (size) {
            case 0 -> null;
            case 1 -> singleLevel.equals(level) ? singleChild : null;
            default -> manyChildren.get(level);
        };
    }

    ImmutableSubscriptionChildren put(String level, ImmutableSubscriptionTreeNode child) {
        return switch (size) {
            case 0 -> singleton(level, child);
            case 1 -> putIntoSingleton(level, child);
            default -> putIntoMany(level, child);
        };
    }

    RemoveResult remove(String level) {
        return switch (size) {
            case 0 -> new RemoveResult(this, false);
            case 1 -> removeFromSingleton(level);
            default -> removeFromMany(level);
        };
    }

    void forEachChild(Consumer<ImmutableSubscriptionTreeNode> consumer) {
        switch (size) {
            case 0 -> {
            }
            case 1 -> consumer.accept(singleChild);
            default -> manyChildren.values().forEach(consumer);
        }
    }

    private ImmutableSubscriptionChildren putIntoSingleton(String level, ImmutableSubscriptionTreeNode child) {
        if (singleLevel.equals(level)) {
            return singleChild == child ? this : singleton(level, child);
        }

        Map<String, ImmutableSubscriptionTreeNode> children = new HashMap<>(4);
        children.put(singleLevel, singleChild);
        children.put(level, child);
        return new ImmutableSubscriptionChildren(2, null, null, children);
    }

    private ImmutableSubscriptionChildren putIntoMany(String level, ImmutableSubscriptionTreeNode child) {
        ImmutableSubscriptionTreeNode existing = manyChildren.get(level);
        if (existing == child) {
            return this;
        }

        Map<String, ImmutableSubscriptionTreeNode> children = new HashMap<>(manyChildren);
        children.put(level, child);
        return new ImmutableSubscriptionChildren(children.size(), null, null, children);
    }

    private RemoveResult removeFromSingleton(String level) {
        if (!singleLevel.equals(level)) {
            return new RemoveResult(this, false);
        }
        return new RemoveResult(empty(), true);
    }

    private RemoveResult removeFromMany(String level) {
        if (!manyChildren.containsKey(level)) {
            return new RemoveResult(this, false);
        }

        Map<String, ImmutableSubscriptionTreeNode> children = new HashMap<>(manyChildren);
        children.remove(level);
        return new RemoveResult(from(children), true);
    }

    private static ImmutableSubscriptionChildren singleton(String level, ImmutableSubscriptionTreeNode child) {
        return new ImmutableSubscriptionChildren(1, level, child, null);
    }

    record RemoveResult(ImmutableSubscriptionChildren children, boolean removed) {
    }
}
