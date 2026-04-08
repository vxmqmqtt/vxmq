package io.github.vxmqmqtt.vxmq.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Repeatable comparison harness between the current subscription tree and a linear-scan baseline.
 */
class SubscriptionRegistryBenchmarkTest {

    private final DefaultTopicMatcher topicMatcher = new DefaultTopicMatcher();

    // Verifies that the subscription tree returns the same matches as the linear baseline across representative workloads.
    @Test
    void shouldMatchSameResultsAsLinearBaselineAcrossRepresentativeScenarios() {
        Scenario exactScenario = exactScenario(5_000);
        Scenario plusScenario = plusWildcardScenario(5_000);
        Scenario hashScenario = hashWildcardScenario(5_000);

        assertEquals(
                baselineMatches(exactScenario).stream().map(SubscriptionBinding::clientId).sorted().toList(),
                treeMatches(exactScenario).stream().map(SubscriptionBinding::clientId).sorted().toList());
        assertEquals(
                baselineMatches(plusScenario).stream().map(SubscriptionBinding::clientId).sorted().toList(),
                treeMatches(plusScenario).stream().map(SubscriptionBinding::clientId).sorted().toList());
        assertEquals(
                baselineMatches(hashScenario).stream().map(SubscriptionBinding::clientId).sorted().toList(),
                treeMatches(hashScenario).stream().map(SubscriptionBinding::clientId).sorted().toList());
    }

    // Verifies that the benchmark harness can produce stable timing output for tree and linear implementations.
    @Test
    void shouldPrintBenchmarkTimingsForRepresentativeScenarios() {
        List<Scenario> scenarios = List.of(
                exactScenario(10_000),
                plusWildcardScenario(10_000),
                hashWildcardScenario(10_000),
                mixedScenario(10_000));

        for (Scenario scenario : scenarios) {
            long baselineNanos = measureBaseline(scenario);
            long treeNanos = measureTree(scenario);
            System.out.printf(
                    "scenario=%s baselineNanos=%d treeNanos=%d matches=%d%n",
                    scenario.name(),
                    baselineNanos,
                    treeNanos,
                    treeMatches(scenario).size());
        }
    }

    private Collection<SubscriptionBinding> baselineMatches(Scenario scenario) {
        LinearScanSubscriptionRegistry registry = new LinearScanSubscriptionRegistry(topicMatcher);
        scenario.bindings().forEach(registry::addSubscription);
        return registry.match(scenario.topicName());
    }

    private Collection<SubscriptionBinding> treeMatches(Scenario scenario) {
        InMemorySubscriptionRegistry registry = new InMemorySubscriptionRegistry(topicMatcher);
        scenario.bindings().forEach(registry::addSubscription);
        return registry.match(scenario.topicName());
    }

    private long measureBaseline(Scenario scenario) {
        LinearScanSubscriptionRegistry registry = new LinearScanSubscriptionRegistry(topicMatcher);
        scenario.bindings().forEach(registry::addSubscription);
        return measure(() -> registry.match(scenario.topicName()));
    }

    private long measureTree(Scenario scenario) {
        InMemorySubscriptionRegistry registry = new InMemorySubscriptionRegistry(topicMatcher);
        scenario.bindings().forEach(registry::addSubscription);
        return measure(() -> registry.match(scenario.topicName()));
    }

    private long measure(Runnable runnable) {
        long start = System.nanoTime();
        for (int i = 0; i < 200; i++) {
            runnable.run();
        }
        return System.nanoTime() - start;
    }

    private Scenario exactScenario(int count) {
        List<SubscriptionBinding> bindings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            bindings.add(new SubscriptionBinding("client-exact-" + i, "sensors/room-" + i + "/temperature", MqttQoS.AT_MOST_ONCE));
        }
        return new Scenario("exact", bindings, "sensors/room-7777/temperature");
    }

    private Scenario plusWildcardScenario(int count) {
        List<SubscriptionBinding> bindings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            bindings.add(new SubscriptionBinding("client-plus-" + i, "sensors/+/temperature", MqttQoS.AT_MOST_ONCE));
        }
        return new Scenario("plus", bindings, "sensors/room-7777/temperature");
    }

    private Scenario hashWildcardScenario(int count) {
        List<SubscriptionBinding> bindings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            bindings.add(new SubscriptionBinding("client-hash-" + i, "sensors/#", MqttQoS.AT_MOST_ONCE));
        }
        return new Scenario("hash", bindings, "sensors/room-7777/floor-2/temperature");
    }

    private Scenario mixedScenario(int count) {
        List<SubscriptionBinding> bindings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            bindings.add(new SubscriptionBinding("client-mixed-exact-" + i, "sensors/room-" + i + "/temperature", MqttQoS.AT_MOST_ONCE));
            bindings.add(new SubscriptionBinding("client-mixed-plus-" + i, "sensors/+/temperature", MqttQoS.AT_LEAST_ONCE));
            bindings.add(new SubscriptionBinding("client-mixed-hash-" + i, "sensors/#", MqttQoS.AT_MOST_ONCE));
        }
        return new Scenario("mixed", bindings, "sensors/room-7777/temperature");
    }

    private record Scenario(String name, List<SubscriptionBinding> bindings, String topicName) {
    }
}
