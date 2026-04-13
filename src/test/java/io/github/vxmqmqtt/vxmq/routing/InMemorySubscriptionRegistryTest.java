package io.github.vxmqmqtt.vxmq.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies subscription tree behavior through the public routing registry API.
 */
class InMemorySubscriptionRegistryTest {

    private final DefaultMqttTopicSupport mqttTopicSupport = new DefaultMqttTopicSupport();

    // Verifies that exact, single-level wildcard, and multi-level wildcard subscriptions can all be matched together.
    @Test
    void shouldMatchExactAndWildcardSubscriptions() {
        InMemorySubscriptionRegistry registry = new InMemorySubscriptionRegistry(mqttTopicSupport);
        registry.addSubscription(new SubscriptionBinding("client-exact", "sensors/room-1/temperature", MqttQoS.AT_MOST_ONCE));
        registry.addSubscription(new SubscriptionBinding("client-plus", "sensors/+/temperature", MqttQoS.AT_LEAST_ONCE));
        registry.addSubscription(new SubscriptionBinding("client-hash", "sensors/#", MqttQoS.AT_MOST_ONCE));

        Collection<SubscriptionBinding> matches = registry.match("sensors/room-1/temperature");

        assertEquals(List.of("client-exact", "client-hash", "client-plus"),
                matches.stream().map(SubscriptionBinding::clientId).sorted().toList());
    }

    // Verifies that overlapping subscriptions for the same client are deduplicated and the highest granted QoS wins.
    @Test
    void shouldDeduplicateOverlappingSubscriptionsByClientAndHighestQos() {
        InMemorySubscriptionRegistry registry = new InMemorySubscriptionRegistry(mqttTopicSupport);
        registry.addSubscription(new SubscriptionBinding("client-a", "sensors/#", MqttQoS.AT_MOST_ONCE));
        registry.addSubscription(new SubscriptionBinding("client-a", "sensors/+/temperature", MqttQoS.AT_LEAST_ONCE));
        registry.addSubscription(new SubscriptionBinding("client-b", "sensors/+/temperature", MqttQoS.AT_MOST_ONCE));

        List<SubscriptionBinding> matches = registry.match("sensors/room-1/temperature")
                .stream()
                .sorted(Comparator.comparing(SubscriptionBinding::clientId))
                .toList();

        assertEquals(2, matches.size());
        assertEquals("client-a", matches.get(0).clientId());
        assertEquals(MqttQoS.AT_LEAST_ONCE, matches.get(0).grantedQos());
        assertEquals("client-b", matches.get(1).clientId());
    }

    // Verifies that removing the last binding on a path prunes now-unused tree nodes.
    @Test
    void shouldPruneUnusedTreeNodesAfterRemovingLastBinding() {
        InMemorySubscriptionRegistry registry = new InMemorySubscriptionRegistry(mqttTopicSupport);
        registry.addSubscription(new SubscriptionBinding("client-a", "sensors/room-1/temperature", MqttQoS.AT_MOST_ONCE));

        int nodeCountAfterInsert = registry.nodeCount();
        boolean removed = registry.removeSubscription("client-a", "sensors/room-1/temperature");

        assertTrue(removed);
        assertEquals(1, registry.nodeCount());
        assertTrue(nodeCountAfterInsert > registry.nodeCount());
        assertTrue(registry.match("sensors/room-1/temperature").isEmpty());
    }

    // Verifies that multi-level wildcard bindings attached to an intermediate node still match deeper topics.
    @Test
    void shouldMatchHashBindingsStoredAtIntermediateNodes() {
        InMemorySubscriptionRegistry registry = new InMemorySubscriptionRegistry(mqttTopicSupport);
        registry.addSubscription(new SubscriptionBinding("client-a", "sensors/room-1/#", MqttQoS.AT_MOST_ONCE));
        registry.addSubscription(new SubscriptionBinding("client-b", "sensors/room-1/temperature", MqttQoS.AT_MOST_ONCE));

        List<String> matches = registry.match("sensors/room-1/floor-2/current")
                .stream()
                .map(SubscriptionBinding::clientId)
                .sorted()
                .toList();

        assertEquals(List.of("client-a"), matches);
    }

    // Verifies that batch snapshot replacement builds the same routing result as incremental subscription registration.
    @Test
    void shouldBuildEquivalentSnapshotWhenReplacingAllSubscriptions() {
        List<SubscriptionBinding> bindings = List.of(
                new SubscriptionBinding("client-a", "sensors/room-1/temperature", MqttQoS.AT_MOST_ONCE),
                new SubscriptionBinding("client-b", "sensors/+/temperature", MqttQoS.AT_LEAST_ONCE),
                new SubscriptionBinding("client-c", "sensors/#", MqttQoS.AT_MOST_ONCE),
                new SubscriptionBinding("client-d", "alerts/room-1/#", MqttQoS.AT_MOST_ONCE));
        InMemorySubscriptionRegistry incrementalRegistry = new InMemorySubscriptionRegistry(mqttTopicSupport);
        bindings.forEach(incrementalRegistry::addSubscription);
        InMemorySubscriptionRegistry batchRegistry = new InMemorySubscriptionRegistry(mqttTopicSupport);

        batchRegistry.replaceAllSubscriptions(bindings);

        for (Map.Entry<String, List<String>> entry : Map.<String, List<String>>of(
                        "sensors/room-1/temperature", List.of("client-a", "client-b", "client-c"),
                        "alerts/room-1/fire", List.of("client-d"),
                        "alerts/room-2/fire", List.of())
                .entrySet()) {
            assertEquals(
                    incrementalRegistry.match(entry.getKey()).stream()
                            .map(SubscriptionBinding::clientId)
                            .sorted()
                            .toList(),
                    batchRegistry.match(entry.getKey()).stream()
                            .map(SubscriptionBinding::clientId)
                            .sorted()
                            .toList(),
                    "topic=" + entry.getKey());
        }
    }

    // Verifies that batch snapshot replacement discards bindings that are no longer present in the authoritative input set.
    @Test
    void shouldReplacePreviousSnapshotWhenReplacingAllSubscriptions() {
        InMemorySubscriptionRegistry registry = new InMemorySubscriptionRegistry(mqttTopicSupport);
        registry.addSubscription(new SubscriptionBinding("client-old", "legacy/bridge/#", MqttQoS.AT_MOST_ONCE));

        registry.replaceAllSubscriptions(List.of(
                new SubscriptionBinding("client-new", "sensors/current/temperature", MqttQoS.AT_LEAST_ONCE),
                new SubscriptionBinding("client-plus", "sensors/+/temperature", MqttQoS.AT_MOST_ONCE)));

        assertTrue(registry.match("legacy/bridge/temperature").isEmpty());
        assertEquals(
                List.of("client-new", "client-plus"),
                registry.match("sensors/current/temperature").stream()
                        .map(SubscriptionBinding::clientId)
                        .sorted()
                        .toList());
    }

    // Verifies that batch snapshot rebuild preserves routing results for a high-fanout exact-child level.
    @Test
    void shouldPreserveHighFanoutMatchesWhenReplacingAllSubscriptions() {
        List<SubscriptionBinding> bindings = java.util.stream.IntStream.range(0, 128)
                .mapToObj(index -> new SubscriptionBinding(
                        "client-" + index,
                        "sensors/device-" + index + "/temperature",
                        MqttQoS.AT_MOST_ONCE))
                .toList();
        InMemorySubscriptionRegistry incrementalRegistry = new InMemorySubscriptionRegistry(mqttTopicSupport);
        bindings.forEach(incrementalRegistry::addSubscription);
        InMemorySubscriptionRegistry batchRegistry = new InMemorySubscriptionRegistry(mqttTopicSupport);

        batchRegistry.replaceAllSubscriptions(bindings);

        assertEquals(
                incrementalRegistry.match("sensors/device-64/temperature").stream()
                        .map(SubscriptionBinding::clientId)
                        .sorted()
                        .toList(),
                batchRegistry.match("sensors/device-64/temperature").stream()
                        .map(SubscriptionBinding::clientId)
                        .sorted()
                        .toList());
    }
}
