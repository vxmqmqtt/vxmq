package io.github.vxmqmqtt.vxmq.routing.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Verifies that all routing concurrency candidates preserve MQTT matching semantics under shared stress.
 */
class RoutingRegistryConcurrencyTest {

    // Verifies that each candidate returns the same steady-state matches as the current subscription-tree semantics.
    @TestFactory
    Stream<DynamicTest> shouldMatchSameResultsAsBaselineAcrossRepresentativeQueries() {
        return allCandidates().stream().map(factory -> DynamicTest.dynamicTest(factory.name(), () -> {
            try (RoutingRegistryCandidate candidate = factory.create()) {
                List<SubscriptionBinding> bindings = List.of(
                        new SubscriptionBinding("client-exact", "devices/site-1/temperature", MqttQoS.AT_MOST_ONCE),
                        new SubscriptionBinding("client-plus", "devices/+/temperature", MqttQoS.AT_LEAST_ONCE),
                        new SubscriptionBinding("client-hash", "devices/#", MqttQoS.AT_MOST_ONCE),
                        new SubscriptionBinding("client-overlap", "devices/site-1/#", MqttQoS.AT_MOST_ONCE),
                        new SubscriptionBinding("client-overlap", "devices/+/temperature", MqttQoS.AT_LEAST_ONCE));
                bindings.forEach(candidate::addSubscription);

                List<SubscriptionBinding> matches = sorted(candidate.match("devices/site-1/temperature"));

                assertEquals(List.of("client-exact", "client-hash", "client-overlap", "client-plus"),
                        matches.stream().map(SubscriptionBinding::clientId).toList());
                assertEquals(MqttQoS.AT_LEAST_ONCE, matches.get(2).grantedQos());
            }
        }));
    }

    // Verifies that thread-safe candidates survive concurrent match/update pressure without corruption, exceptions, or leaked bindings.
    @TestFactory
    Stream<DynamicTest> shouldRemainConsistentDuringConcurrentMatchesAndUpdates() {
        return threadSafeCandidates().stream().map(factory -> DynamicTest.dynamicTest(factory.name(), () -> {
            try (RoutingRegistryCandidate candidate = factory.create()) {
                candidate.addSubscription(new SubscriptionBinding("stable-exact", "devices/site-1/temperature", MqttQoS.AT_MOST_ONCE));
                candidate.addSubscription(new SubscriptionBinding("stable-plus", "devices/+/temperature", MqttQoS.AT_LEAST_ONCE));
                candidate.addSubscription(new SubscriptionBinding("stable-hash", "devices/#", MqttQoS.AT_MOST_ONCE));

                ExecutorService executor = Executors.newFixedThreadPool(8);
                CountDownLatch ready = new CountDownLatch(8);
                CountDownLatch start = new CountDownLatch(1);
                ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
                try {
                    for (int worker = 0; worker < 4; worker++) {
                        executor.submit(() -> {
                            ready.countDown();
                            await(start, failures);
                            for (int iteration = 0; iteration < 1_000; iteration++) {
                                String clientId = "churn-" + iteration % 32;
                                String topicFilter = "devices/site-" + (iteration % 32) + "/temperature";
                                try {
                                    candidate.addSubscription(new SubscriptionBinding(clientId, topicFilter, MqttQoS.AT_MOST_ONCE));
                                    candidate.removeSubscription(clientId, topicFilter);
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
                            for (int iteration = 0; iteration < 2_000; iteration++) {
                                try {
                                    Collection<SubscriptionBinding> matches = candidate.match("devices/site-1/temperature");
                                    Set<String> clientIds = matches.stream().map(SubscriptionBinding::clientId).collect(java.util.stream.Collectors.toSet());
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

                List<SubscriptionBinding> finalMatches = sorted(candidate.match("devices/site-1/temperature"));
                assertEquals(List.of("stable-exact", "stable-hash", "stable-plus"),
                        finalMatches.stream().map(SubscriptionBinding::clientId).toList());
            }
        }));
    }

    private void await(CountDownLatch latch, ConcurrentLinkedQueue<Throwable> failures) {
        try {
            latch.await();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            failures.add(interruptedException);
        }
    }

    private List<SubscriptionBinding> sorted(Collection<SubscriptionBinding> matches) {
        return matches.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SubscriptionBinding::clientId))
                .toList();
    }

    private List<RoutingRegistryCandidateFactory> allCandidates() {
        return List.of(
                new SynchronizedTreeRoutingCandidate.Factory(),
                new SnapshotTreeRoutingCandidate.Factory(),
                new VerticleOwnedRoutingCandidate.Factory());
    }

    private List<RoutingRegistryCandidateFactory> threadSafeCandidates() {
        return allCandidates();
    }
}
