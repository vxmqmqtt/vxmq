package io.github.vxmqmqtt.vxmq.routing;

import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Produces routing write-path diagnostics that separate single-update cost from whole-snapshot rebuild cost.
 */
@Tag("routing-eval")
class InMemorySubscriptionRegistryWritePathBenchmarkTest {

    private final DefaultMqttTopicSupport mqttTopicSupport = new DefaultMqttTopicSupport();

    // Verifies that write-path diagnostics can be printed for single add/remove operations and batch snapshot replacement.
    @Test
    void shouldPrintSingleUpdateAndBatchRebuildTimings() {
        List<SubscriptionBinding> baselineBindings = exactScenario(10_000);
        SubscriptionBinding churnBinding =
                new SubscriptionBinding("diagnostic-churn", "sensors/churn/temperature", MqttQoS.AT_MOST_ONCE);
        InMemorySubscriptionRegistry addRegistry = new InMemorySubscriptionRegistry(mqttTopicSupport);
        addRegistry.replaceAllSubscriptions(baselineBindings);
        InMemorySubscriptionRegistry removeRegistry = new InMemorySubscriptionRegistry(mqttTopicSupport);
        removeRegistry.replaceAllSubscriptions(baselineBindings);
        removeRegistry.addSubscription(churnBinding);

        long singleAddCycleNanos = measureRepeated(20_000, () -> {
            addRegistry.addSubscription(churnBinding);
            addRegistry.removeSubscription(churnBinding.clientId(), churnBinding.topicFilter());
        });

        long singleRemoveCycleNanos = measureRepeated(20_000, () -> {
            removeRegistry.removeSubscription(churnBinding.clientId(), churnBinding.topicFilter());
            removeRegistry.addSubscription(churnBinding);
        });

        long incrementalLoadNanos = measureRepeated(20, () -> {
            InMemorySubscriptionRegistry registry = new InMemorySubscriptionRegistry(mqttTopicSupport);
            baselineBindings.forEach(registry::addSubscription);
        });

        long builderLoadNanos = measureRepeated(20, () -> {
            MutableSubscriptionTreeBuilder builder = new MutableSubscriptionTreeBuilder(mqttTopicSupport);
            builder.addAll(baselineBindings);
        });

        MutableSubscriptionTreeBuilder preparedBuilder = new MutableSubscriptionTreeBuilder(mqttTopicSupport);
        preparedBuilder.addAll(baselineBindings);
        long freezeToImmutableNanos = measureRepeated(20, preparedBuilder::build);

        long batchReplaceNanos = measureRepeated(20, () -> {
            InMemorySubscriptionRegistry registry = new InMemorySubscriptionRegistry(mqttTopicSupport);
            registry.replaceAllSubscriptions(baselineBindings);
        });

        System.out.printf(
                "candidate=production-snapshot-tree workload=write-diagnostics singleAddCycleNanos=%d singleRemoveCycleNanos=%d incrementalLoadNanos=%d builderLoadNanos=%d freezeToImmutableNanos=%d batchReplaceNanos=%d%n",
                singleAddCycleNanos,
                singleRemoveCycleNanos,
                incrementalLoadNanos,
                builderLoadNanos,
                freezeToImmutableNanos,
                batchReplaceNanos);
    }

    private List<SubscriptionBinding> exactScenario(int subscriptionCount) {
        List<SubscriptionBinding> bindings = new ArrayList<>(subscriptionCount);
        for (int index = 0; index < subscriptionCount; index++) {
            bindings.add(new SubscriptionBinding(
                    "client-" + index,
                    "sensors/device-" + index + "/temperature",
                    MqttQoS.AT_MOST_ONCE));
        }
        return bindings;
    }

    private long measureRepeated(int repetitions, Runnable runnable) {
        long start = System.nanoTime();
        for (int iteration = 0; iteration < repetitions; iteration++) {
            runnable.run();
        }
        return System.nanoTime() - start;
    }
}
