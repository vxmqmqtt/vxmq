package io.github.vxmqmqtt.vxmq.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies representation transitions inside the immutable child-node container.
 */
class ImmutableSubscriptionChildrenTest {

    // Verifies that child lookup remains correct while the container grows from singleton to small to bucketed and back.
    @Test
    void shouldPreserveLookupsAcrossRepresentationTransitions() {
        ImmutableSubscriptionChildren children = ImmutableSubscriptionChildren.empty();
        ImmutableSubscriptionTreeNode childA = child("client-a");
        ImmutableSubscriptionTreeNode childB = child("client-b");
        ImmutableSubscriptionTreeNode childC = child("client-c");
        ImmutableSubscriptionTreeNode childD = child("client-d");
        ImmutableSubscriptionTreeNode childE = child("client-e");

        children = children.put("a", childA);
        children = children.put("b", childB);
        children = children.put("c", childC);
        children = children.put("d", childD);
        children = children.put("e", childE);

        assertSame(childA, children.get("a"));
        assertSame(childE, children.get("e"));

        children = children.remove("e").children();
        children = children.remove("d").children();
        children = children.remove("c").children();

        assertSame(childA, children.get("a"));
        assertSame(childB, children.get("b"));
        assertNull(children.get("e"));
    }

    // Verifies that removing a missing child from a bucketed container reports false and leaves the original instance unchanged.
    @Test
    void shouldKeepSameInstanceWhenRemovingUnknownChild() {
        ImmutableSubscriptionChildren children = ImmutableSubscriptionChildren.empty();
        for (int index = 0; index < 6; index++) {
            children = children.put("level-" + index, child("client-" + index));
        }

        ImmutableSubscriptionChildren.RemoveResult result = children.remove("missing");

        assertTrue(!result.removed());
        assertSame(children, result.children());
        assertEquals(6, count(children));
    }

    private ImmutableSubscriptionTreeNode child(String clientId) {
        SubscriptionBinding binding =
                new SubscriptionBinding(clientId, "devices/" + clientId + "/temperature", MqttQoS.AT_MOST_ONCE);
        return ImmutableSubscriptionTreeNode.create(
                ImmutableSubscriptionChildren.empty(),
                null,
                ImmutableSubscriptionBindings.from(Map.of(binding.clientId(), binding)),
                ImmutableSubscriptionBindings.empty());
    }

    private int count(ImmutableSubscriptionChildren children) {
        int[] count = new int[1];
        children.forEachChild(ignored -> count[0]++);
        return count[0];
    }
}
