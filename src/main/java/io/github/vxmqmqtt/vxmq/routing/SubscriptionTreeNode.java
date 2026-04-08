package io.github.vxmqmqtt.vxmq.routing;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One node in the in-memory subscription tree.
 */
final class SubscriptionTreeNode {

    private final Map<String, SubscriptionTreeNode> exactChildren = new LinkedHashMap<>();
    private SubscriptionTreeNode singleLevelWildcardChild;
    private final Map<String, SubscriptionBinding> terminalBindings = new LinkedHashMap<>();
    private final Map<String, SubscriptionBinding> multiLevelWildcardBindings = new LinkedHashMap<>();

    Map<String, SubscriptionTreeNode> exactChildren() {
        return exactChildren;
    }

    SubscriptionTreeNode singleLevelWildcardChild() {
        return singleLevelWildcardChild;
    }

    SubscriptionTreeNode ensureSingleLevelWildcardChild() {
        if (singleLevelWildcardChild == null) {
            singleLevelWildcardChild = new SubscriptionTreeNode();
        }
        return singleLevelWildcardChild;
    }

    void clearSingleLevelWildcardChildIfUnused() {
        if (singleLevelWildcardChild != null && singleLevelWildcardChild.isEmpty()) {
            singleLevelWildcardChild = null;
        }
    }

    Map<String, SubscriptionBinding> terminalBindings() {
        return terminalBindings;
    }

    Map<String, SubscriptionBinding> multiLevelWildcardBindings() {
        return multiLevelWildcardBindings;
    }

    boolean isEmpty() {
        return exactChildren.isEmpty()
                && singleLevelWildcardChild == null
                && terminalBindings.isEmpty()
                && multiLevelWildcardBindings.isEmpty();
    }

    int nodeCount() {
        int count = 1;
        for (SubscriptionTreeNode child : exactChildren.values()) {
            count += child.nodeCount();
        }
        if (singleLevelWildcardChild != null) {
            count += singleLevelWildcardChild.nodeCount();
        }
        return count;
    }
}
