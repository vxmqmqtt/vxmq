package io.github.vxmqmqtt.vxmq.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the production routing registry preserves correctness under concurrent reads and updates.
 */
class InMemorySubscriptionRegistryConcurrencyTest {

    private final DefaultMqttTopicSupport mqttTopicSupport = new DefaultMqttTopicSupport();

    // Verifies that concurrent match and churn operations do not corrupt the tree or duplicate client deliveries.
    @Test
    void shouldRemainConsistentDuringConcurrentMatchesAndUpdates() throws Exception {
        InMemorySubscriptionRegistry registry = new InMemorySubscriptionRegistry(mqttTopicSupport);
        registry.addSubscription(new SubscriptionBinding("stable-exact", "devices/site-1/temperature", MqttQoS.AT_MOST_ONCE));
        registry.addSubscription(new SubscriptionBinding("stable-plus", "devices/+/temperature", MqttQoS.AT_LEAST_ONCE));
        registry.addSubscription(new SubscriptionBinding("stable-hash", "devices/#", MqttQoS.AT_MOST_ONCE));

        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        try {
            for (int worker = 0; worker < 4; worker++) {
                executor.submit(() -> {
                    ready.countDown();
                    await(start, failures);
                    for (int iteration = 0; iteration < 2_000; iteration++) {
                        String clientId = "churn-" + iteration % 64;
                        String topicFilter = "devices/site-" + (iteration % 64) + "/temperature";
                        try {
                            registry.addSubscription(new SubscriptionBinding(clientId, topicFilter, MqttQoS.AT_MOST_ONCE));
                            registry.removeSubscription(clientId, topicFilter);
                        } catch (Throwable throwable) {
                            failures.add(throwable);
                            return;
                        }
                    }
                });
            }

            for (int worker = 0; worker < 4; worker++) {
                executor.submit(() -> {
                    ready.countDown();
                    await(start, failures);
                    for (int iteration = 0; iteration < 4_000; iteration++) {
                        try {
                            Collection<SubscriptionBinding> matches = registry.match("devices/site-1/temperature");
                            Set<String> clientIds = matches.stream()
                                    .map(SubscriptionBinding::clientId)
                                    .collect(Collectors.toSet());
                            if (clientIds.size() != matches.size()) {
                                failures.add(new AssertionError("Duplicate client delivery detected"));
                                return;
                            }
                        } catch (Throwable throwable) {
                            failures.add(throwable);
                            return;
                        }
                    }
                });
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertTrue(failures.isEmpty(), () -> "Unexpected concurrent routing failures: " + failures);

        List<String> finalMatches = registry.match("devices/site-1/temperature")
                .stream()
                .map(SubscriptionBinding::clientId)
                .sorted()
                .toList();
        assertEquals(List.of("stable-exact", "stable-hash", "stable-plus"), finalMatches);
    }

    private void await(CountDownLatch latch, ConcurrentLinkedQueue<Throwable> failures) {
        try {
            latch.await();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            failures.add(interruptedException);
        }
    }
}
