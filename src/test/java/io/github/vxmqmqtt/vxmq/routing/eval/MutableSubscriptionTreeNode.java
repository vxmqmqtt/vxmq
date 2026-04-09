package io.github.vxmqmqtt.vxmq.routing.eval;

import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test-only mutable node used by the unsafe baseline and owner-based evaluation candidates.
 */
final class MutableSubscriptionTreeNode {

    private final Map<String, MutableSubscriptionTreeNode> exactChildren = new LinkedHashMap<>();
    private MutableSubscriptionTreeNode singleLevelWildcardChild;
    private final Map<String, SubscriptionBinding> terminalBindings = new LinkedHashMap<>();
    private final Map<String, SubscriptionBinding> multiLevelWildcardBindings = new LinkedHashMap<>();

    Map<String, MutableSubscriptionTreeNode> exactChildren() {
        return exactChildren;
    }

    MutableSubscriptionTreeNode singleLevelWildcardChild() {
        return singleLevelWildcardChild;
    }

    MutableSubscriptionTreeNode ensureSingleLevelWildcardChild() {
        if (singleLevelWildcardChild == null) {
            singleLevelWildcardChild = new MutableSubscriptionTreeNode();
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
}
