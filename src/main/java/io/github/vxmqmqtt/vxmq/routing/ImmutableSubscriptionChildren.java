package io.github.vxmqmqtt.vxmq.routing;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Compact immutable child-node container optimized for empty/single/small cases and sharded for large fan-out levels.
 */
final class ImmutableSubscriptionChildren {

    private static final int SMALL_MAX = 4;
    private static final int BUCKET_COUNT = 64;
    private static final int BUCKET_MASK = BUCKET_COUNT - 1;

    private static final ImmutableSubscriptionChildren EMPTY =
            new ImmutableSubscriptionChildren(0, null, null, null, null, null);

    private final int size;
    private final String singleLevel;
    private final ImmutableSubscriptionTreeNode singleChild;
    private final String[] smallLevels;
    private final ImmutableSubscriptionTreeNode[] smallChildren;
    private final Object[] bucketChildren;

    private ImmutableSubscriptionChildren(
            int size,
            String singleLevel,
            ImmutableSubscriptionTreeNode singleChild,
            String[] smallLevels,
            ImmutableSubscriptionTreeNode[] smallChildren,
            Object[] bucketChildren) {
        this.size = size;
        this.singleLevel = singleLevel;
        this.singleChild = singleChild;
        this.smallLevels = smallLevels;
        this.smallChildren = smallChildren;
        this.bucketChildren = bucketChildren;
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
        if (children.size() <= SMALL_MAX) {
            return small(children);
        }
        return bucketed(children);
    }

    static ImmutableSubscriptionChildren fromSmallSnapshot(
            String[] levels,
            ImmutableSubscriptionTreeNode[] children) {
        if (levels.length != children.length) {
            throw new IllegalArgumentException("Levels and children must have the same size");
        }
        if (levels.length == 0) {
            return empty();
        }
        if (levels.length == 1) {
            return singleton(levels[0], children[0]);
        }
        if (levels.length > SMALL_MAX) {
            throw new IllegalArgumentException("Small snapshot cannot exceed " + SMALL_MAX + " entries");
        }
        return new ImmutableSubscriptionChildren(
                levels.length,
                null,
                null,
                levels.clone(),
                children.clone(),
                null);
    }

    static ImmutableSubscriptionChildren fromBucketSnapshot(Object[] bucketChildren, int size) {
        if (size == 0) {
            return empty();
        }
        if (size <= SMALL_MAX) {
            throw new IllegalArgumentException("Bucket snapshot requires more than " + SMALL_MAX + " entries");
        }
        if (bucketChildren.length != BUCKET_COUNT) {
            throw new IllegalArgumentException("Bucket snapshot must contain " + BUCKET_COUNT + " buckets");
        }
        return new ImmutableSubscriptionChildren(size, null, null, null, null, bucketChildren.clone());
    }

    boolean isEmpty() {
        return size == 0;
    }

    ImmutableSubscriptionTreeNode get(String level) {
        return switch (representation()) {
            case EMPTY -> null;
            case SINGLE -> singleLevel.equals(level) ? singleChild : null;
            case SMALL -> getFromSmall(level);
            case BUCKETED -> bucket(level).get(level);
        };
    }

    ImmutableSubscriptionChildren put(String level, ImmutableSubscriptionTreeNode child) {
        return switch (representation()) {
            case EMPTY -> singleton(level, child);
            case SINGLE -> putIntoSingleton(level, child);
            case SMALL -> putIntoSmall(level, child);
            case BUCKETED -> putIntoBuckets(level, child);
        };
    }

    RemoveResult remove(String level) {
        return switch (representation()) {
            case EMPTY -> new RemoveResult(this, false);
            case SINGLE -> removeFromSingleton(level);
            case SMALL -> removeFromSmall(level);
            case BUCKETED -> removeFromBuckets(level);
        };
    }

    void forEachChild(Consumer<ImmutableSubscriptionTreeNode> consumer) {
        switch (representation()) {
            case EMPTY -> {
            }
            case SINGLE -> consumer.accept(singleChild);
            case SMALL -> {
                for (ImmutableSubscriptionTreeNode child : smallChildren) {
                    consumer.accept(child);
                }
            }
            case BUCKETED -> {
                for (Object bucket : bucketChildren) {
                    if (bucket != null) {
                        typedBucket(bucket).values().forEach(consumer);
                    }
                }
            }
        }
    }

    private Representation representation() {
        if (size == 0) {
            return Representation.EMPTY;
        }
        if (size == 1) {
            return Representation.SINGLE;
        }
        if (size <= SMALL_MAX) {
            return Representation.SMALL;
        }
        return Representation.BUCKETED;
    }

    private ImmutableSubscriptionTreeNode getFromSmall(String level) {
        for (int index = 0; index < smallLevels.length; index++) {
            if (smallLevels[index].equals(level)) {
                return smallChildren[index];
            }
        }
        return null;
    }

    private ImmutableSubscriptionChildren putIntoSingleton(String level, ImmutableSubscriptionTreeNode child) {
        if (singleLevel.equals(level)) {
            return singleChild == child ? this : singleton(level, child);
        }
        return new ImmutableSubscriptionChildren(
                2,
                null,
                null,
                new String[] {singleLevel, level},
                new ImmutableSubscriptionTreeNode[] {singleChild, child},
                null);
    }

    private ImmutableSubscriptionChildren putIntoSmall(String level, ImmutableSubscriptionTreeNode child) {
        int existingIndex = indexOfSmall(level);
        if (existingIndex >= 0) {
            if (smallChildren[existingIndex] == child) {
                return this;
            }
            ImmutableSubscriptionTreeNode[] updatedChildren = smallChildren.clone();
            updatedChildren[existingIndex] = child;
            return new ImmutableSubscriptionChildren(size, null, null, smallLevels, updatedChildren, null);
        }

        if (size < SMALL_MAX) {
            String[] updatedLevels = new String[size + 1];
            ImmutableSubscriptionTreeNode[] updatedChildren = new ImmutableSubscriptionTreeNode[size + 1];
            System.arraycopy(smallLevels, 0, updatedLevels, 0, size);
            System.arraycopy(smallChildren, 0, updatedChildren, 0, size);
            updatedLevels[size] = level;
            updatedChildren[size] = child;
            return new ImmutableSubscriptionChildren(size + 1, null, null, updatedLevels, updatedChildren, null);
        }

        Map<String, ImmutableSubscriptionTreeNode> children = new HashMap<>(size + 1);
        for (int index = 0; index < size; index++) {
            children.put(smallLevels[index], smallChildren[index]);
        }
        children.put(level, child);
        return bucketed(children);
    }

    private ImmutableSubscriptionChildren putIntoBuckets(String level, ImmutableSubscriptionTreeNode child) {
        int bucketIndex = bucketIndex(level);
        Map<String, ImmutableSubscriptionTreeNode> existingBucket = bucket(level);
        ImmutableSubscriptionTreeNode existingChild = existingBucket.get(level);
        if (existingChild == child) {
            return this;
        }

        Map<String, ImmutableSubscriptionTreeNode> updatedBucket = new HashMap<>(existingBucket);
        updatedBucket.put(level, child);
        Object[] updatedBuckets = bucketChildren.clone();
        updatedBuckets[bucketIndex] = updatedBucket;
        return new ImmutableSubscriptionChildren(
                existingChild == null ? size + 1 : size,
                null,
                null,
                null,
                null,
                updatedBuckets);
    }

    private RemoveResult removeFromSingleton(String level) {
        if (!singleLevel.equals(level)) {
            return new RemoveResult(this, false);
        }
        return new RemoveResult(empty(), true);
    }

    private RemoveResult removeFromSmall(String level) {
        int existingIndex = indexOfSmall(level);
        if (existingIndex < 0) {
            return new RemoveResult(this, false);
        }
        if (size == 2) {
            int remainingIndex = existingIndex == 0 ? 1 : 0;
            return new RemoveResult(singleton(smallLevels[remainingIndex], smallChildren[remainingIndex]), true);
        }

        String[] updatedLevels = new String[size - 1];
        ImmutableSubscriptionTreeNode[] updatedChildren = new ImmutableSubscriptionTreeNode[size - 1];
        copyWithoutIndex(smallLevels, updatedLevels, existingIndex);
        copyWithoutIndex(smallChildren, updatedChildren, existingIndex);
        return new RemoveResult(
                new ImmutableSubscriptionChildren(size - 1, null, null, updatedLevels, updatedChildren, null),
                true);
    }

    private RemoveResult removeFromBuckets(String level) {
        int bucketIndex = bucketIndex(level);
        Map<String, ImmutableSubscriptionTreeNode> existingBucket = bucket(level);
        if (!existingBucket.containsKey(level)) {
            return new RemoveResult(this, false);
        }

        if (size - 1 <= SMALL_MAX) {
            Map<String, ImmutableSubscriptionTreeNode> children = flattenBucketsExcept(level);
            return new RemoveResult(from(children), true);
        }

        Map<String, ImmutableSubscriptionTreeNode> updatedBucket = new HashMap<>(existingBucket);
        updatedBucket.remove(level);
        Object[] updatedBuckets = bucketChildren.clone();
        updatedBuckets[bucketIndex] = updatedBucket.isEmpty() ? null : updatedBucket;
        return new RemoveResult(
                new ImmutableSubscriptionChildren(size - 1, null, null, null, null, updatedBuckets),
                true);
    }

    private int indexOfSmall(String level) {
        for (int index = 0; index < size; index++) {
            if (smallLevels[index].equals(level)) {
                return index;
            }
        }
        return -1;
    }

    private Map<String, ImmutableSubscriptionTreeNode> flattenBucketsExcept(String removedLevel) {
        Map<String, ImmutableSubscriptionTreeNode> flattened = new HashMap<>(size - 1);
        for (Object bucket : bucketChildren) {
            if (bucket == null) {
                continue;
            }
            Map<String, ImmutableSubscriptionTreeNode> typedBucket = typedBucket(bucket);
            typedBucket.forEach((level, child) -> {
                if (!removedLevel.equals(level)) {
                    flattened.put(level, child);
                }
            });
        }
        return flattened;
    }

    @SuppressWarnings("unchecked")
    private Map<String, ImmutableSubscriptionTreeNode> bucket(String level) {
        Object bucket = bucketChildren[bucketIndex(level)];
        return bucket == null ? Map.of() : (Map<String, ImmutableSubscriptionTreeNode>) bucket;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ImmutableSubscriptionTreeNode> typedBucket(Object bucket) {
        return (Map<String, ImmutableSubscriptionTreeNode>) bucket;
    }

    private static int bucketIndex(String level) {
        return level.hashCode() & BUCKET_MASK;
    }

    private static ImmutableSubscriptionChildren singleton(String level, ImmutableSubscriptionTreeNode child) {
        return new ImmutableSubscriptionChildren(1, level, child, null, null, null);
    }

    private static ImmutableSubscriptionChildren small(Map<String, ImmutableSubscriptionTreeNode> children) {
        String[] levels = new String[children.size()];
        ImmutableSubscriptionTreeNode[] values = new ImmutableSubscriptionTreeNode[children.size()];
        int index = 0;
        for (Map.Entry<String, ImmutableSubscriptionTreeNode> entry : children.entrySet()) {
            levels[index] = entry.getKey();
            values[index] = entry.getValue();
            index++;
        }
        return fromSmallSnapshot(levels, values);
    }

    private static ImmutableSubscriptionChildren bucketed(Map<String, ImmutableSubscriptionTreeNode> children) {
        Object[] buckets = new Object[BUCKET_COUNT];
        children.forEach((level, child) -> {
            int bucketIndex = bucketIndex(level);
            @SuppressWarnings("unchecked")
            Map<String, ImmutableSubscriptionTreeNode> bucket =
                    buckets[bucketIndex] == null ? new HashMap<>() : (Map<String, ImmutableSubscriptionTreeNode>) buckets[bucketIndex];
            bucket.put(level, child);
            buckets[bucketIndex] = bucket;
        });
        return fromBucketSnapshot(buckets, children.size());
    }

    private static void copyWithoutIndex(Object[] source, Object[] destination, int removedIndex) {
        if (removedIndex > 0) {
            System.arraycopy(source, 0, destination, 0, removedIndex);
        }
        if (removedIndex < source.length - 1) {
            System.arraycopy(source, removedIndex + 1, destination, removedIndex, source.length - removedIndex - 1);
        }
    }

    private enum Representation {
        EMPTY,
        SINGLE,
        SMALL,
        BUCKETED
    }

    record RemoveResult(ImmutableSubscriptionChildren children, boolean removed) {
    }
}
