package io.github.vxmqmqtt.vxmq.routing.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.vxmqmqtt.vxmq.routing.InMemorySubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Produces repeatable benchmark output for candidate concurrency strategies under the same workloads.
 */
@Tag("routing-eval")
class RoutingRegistryBenchmarkComparisonTest {

    // Verifies that every candidate returns the same match set as the current single-thread tree before timing comparisons are printed.
    @Test
    void shouldMatchSameResultsAsCurrentTreeBeforeBenchmarking() throws Exception {
        List<Scenario> scenarios = List.of(
                exactScenario(5_000),
                plusWildcardScenario(5_000),
                hashWildcardScenario(5_000),
                mixedScenario(5_000));

        for (RoutingRegistryCandidateFactory factory : candidateFactories()) {
            for (Scenario scenario : scenarios) {
                try (RoutingRegistryCandidate candidate = factory.create()) {
                    load(candidate, scenario.bindings());
                    assertEquals(
                            baselineMatches(scenario).stream().map(SubscriptionBinding::clientId).sorted().toList(),
                            candidate.match(scenario.topicName()).stream().map(SubscriptionBinding::clientId).sorted().toList(),
                            "candidate=" + factory.name() + " scenario=" + scenario.name());
                }
            }
        }
    }

    // Verifies that benchmark timings can be emitted for baseline and candidate implementations under representative workloads.
    @Test
    void shouldPrintBenchmarkTimingsForCandidates() throws Exception {
        List<Scenario> scenarios = List.of(
                exactScenario(10_000),
                plusWildcardScenario(10_000),
                hashWildcardScenario(10_000),
                mixedScenario(10_000));

        for (Scenario scenario : scenarios) {
            long unsafeTreeNanos = measureUnsafeTree(scenario);
            System.out.printf("candidate=unsafe-tree scenario=%s nanos=%d matches=%d%n",
                    scenario.name(), unsafeTreeNanos, baselineMatches(scenario).size());
            for (RoutingRegistryCandidateFactory factory : candidateFactories()) {
                try (RoutingRegistryCandidate candidate = factory.create()) {
                    load(candidate, scenario.bindings());
                    long candidateNanos = measure(() -> candidate.match(scenario.topicName()));
                    System.out.printf(
                            "candidate=%s scenario=%s nanos=%d matches=%d%n",
                            factory.name(),
                            scenario.name(),
                            candidateNanos,
                            candidate.match(scenario.topicName()).size());
                }
            }
        }
    }

    // Verifies that candidate comparison also captures add/remove update cost, not only steady-state match timings.
    @Test
    void shouldPrintUpdateTimingsForCandidates() throws Exception {
        List<SubscriptionBinding> bindings = exactScenario(10_000).bindings();

        long unsafeAddNanos = measureUnsafeLoad(bindings);
        long unsafeRemoveNanos = measureUnsafeUnload(bindings);
        System.out.printf("candidate=unsafe-tree workload=updates addNanos=%d removeNanos=%d%n", unsafeAddNanos, unsafeRemoveNanos);

        for (RoutingRegistryCandidateFactory factory : candidateFactories()) {
            long addNanos = measureCandidateLoad(factory, bindings);
            long removeNanos = measureCandidateUnload(factory, bindings);
            System.out.printf(
                    "candidate=%s workload=updates addNanos=%d removeNanos=%d%n",
                    factory.name(),
                    addNanos,
                    removeNanos);
        }
    }

    // Verifies that candidates can be compared under a read-heavy concurrent workload with periodic subscription churn.
    @Test
    void shouldPrintConcurrentReadHeavyBenchmarkForCandidates() throws Exception {
        Scenario scenario = mixedScenario(2_000);

        for (RoutingRegistryCandidateFactory factory : candidateFactories()) {
            try (RoutingRegistryCandidate candidate = factory.create()) {
                load(candidate, scenario.bindings());
                ConcurrentBenchmarkResult result = measureConcurrentReadHeavy(candidate, scenario.topicName());
                System.out.printf(
                        "candidate=%s workload=concurrent-read-heavy matches=%d updates=%d throughputOpsPerSec=%d p50Nanos=%d p95Nanos=%d p99Nanos=%d%n",
                        factory.name(),
                        result.matchOperations(),
                        result.updateOperations(),
                        result.operationsPerSecond(),
                        result.p50Nanos(),
                        result.p95Nanos(),
                        result.p99Nanos());
            }
        }
    }

    private List<RoutingRegistryCandidateFactory> candidateFactories() {
        return List.of(
                new SynchronizedTreeRoutingCandidate.Factory(),
                new SnapshotTreeRoutingCandidate.Factory(),
                new VerticleOwnedRoutingCandidate.Factory());
    }

    private void load(RoutingRegistryCandidate candidate, List<SubscriptionBinding> bindings) {
        bindings.forEach(candidate::addSubscription);
    }

    private Collection<SubscriptionBinding> baselineMatches(Scenario scenario) {
        MutableTreeSubscriptionRegistry registry = new MutableTreeSubscriptionRegistry();
        scenario.bindings().forEach(registry::addSubscription);
        return registry.match(scenario.topicName());
    }

    private long measureUnsafeTree(Scenario scenario) {
        MutableTreeSubscriptionRegistry registry = new MutableTreeSubscriptionRegistry();
        scenario.bindings().forEach(registry::addSubscription);
        return measure(() -> registry.match(scenario.topicName()));
    }

    private long measureUnsafeLoad(List<SubscriptionBinding> bindings) {
        return measureRepeated(20, () -> {
            MutableTreeSubscriptionRegistry registry = new MutableTreeSubscriptionRegistry();
            bindings.forEach(registry::addSubscription);
        });
    }

    private long measureUnsafeUnload(List<SubscriptionBinding> bindings) {
        return measureRepeated(20, () -> {
            MutableTreeSubscriptionRegistry registry = new MutableTreeSubscriptionRegistry();
            bindings.forEach(registry::addSubscription);
            bindings.forEach(binding -> registry.removeSubscription(binding.clientId(), binding.topicFilter()));
        });
    }

    private long measureCandidateLoad(RoutingRegistryCandidateFactory factory, List<SubscriptionBinding> bindings) throws Exception {
        return measureRepeated(20, () -> {
            try (RoutingRegistryCandidate candidate = factory.create()) {
                bindings.forEach(candidate::addSubscription);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private long measureCandidateUnload(RoutingRegistryCandidateFactory factory, List<SubscriptionBinding> bindings) throws Exception {
        return measureRepeated(20, () -> {
            try (RoutingRegistryCandidate candidate = factory.create()) {
                bindings.forEach(candidate::addSubscription);
                bindings.forEach(binding -> candidate.removeSubscription(binding.clientId(), binding.topicFilter()));
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private long measure(Runnable runnable) {
        long start = System.nanoTime();
        for (int i = 0; i < 200; i++) {
            runnable.run();
        }
        return System.nanoTime() - start;
    }

    private long measureRepeated(int repetitions, Runnable runnable) {
        long start = System.nanoTime();
        for (int i = 0; i < repetitions; i++) {
            runnable.run();
        }
        return System.nanoTime() - start;
    }

    private ConcurrentBenchmarkResult measureConcurrentReadHeavy(RoutingRegistryCandidate candidate, String topicName)
            throws Exception {
        int matcherThreads = 4;
        int matchIterationsPerThread = 2_000;
        int updateIterations = 1_000;
        ExecutorService executor = Executors.newFixedThreadPool(matcherThreads + 1);
        CountDownLatch ready = new CountDownLatch(matcherThreads + 1);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        List<Long> matchLatencies = Collections.synchronizedList(new ArrayList<>());
        long startedAt = System.nanoTime();
        try {
            for (int matcherIndex = 0; matcherIndex < matcherThreads; matcherIndex++) {
                executor.submit(() -> {
                    ready.countDown();
                    await(start, failures);
                    for (int iteration = 0; iteration < matchIterationsPerThread; iteration++) {
                        long before = System.nanoTime();
                        try {
                            candidate.match(topicName);
                            matchLatencies.add(System.nanoTime() - before);
                        } catch (Throwable throwable) {
                            failures.add(throwable);
                            return;
                        }
                    }
                });
            }

            executor.submit(() -> {
                ready.countDown();
                await(start, failures);
                for (int iteration = 0; iteration < updateIterations; iteration++) {
                    SubscriptionBinding binding = new SubscriptionBinding(
                            "bench-churn-" + (iteration % 64),
                            "sensors/bench-" + (iteration % 64) + "/temperature",
                            MqttQoS.AT_MOST_ONCE);
                    try {
                        candidate.addSubscription(binding);
                        candidate.removeSubscription(binding.clientId(), binding.topicFilter());
                    } catch (Throwable throwable) {
                        failures.add(throwable);
                        return;
                    }
                }
            });

            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent benchmark workers failed to become ready");
            }
            startedAt = System.nanoTime();
            start.countDown();
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent benchmark workers did not finish in time");
            }
        } finally {
            executor.shutdownNow();
        }

        if (!failures.isEmpty()) {
            throw new AssertionError("Concurrent benchmark failures: " + failures);
        }

        List<Long> sortedLatencies = new ArrayList<>(matchLatencies);
        Collections.sort(sortedLatencies);
        long elapsedNanos = System.nanoTime() - startedAt;
        long totalOperations = (long) matcherThreads * matchIterationsPerThread + updateIterations;
        long operationsPerSecond = (long) (totalOperations * 1_000_000_000.0 / elapsedNanos);
        return new ConcurrentBenchmarkResult(
                (long) matcherThreads * matchIterationsPerThread,
                updateIterations,
                operationsPerSecond,
                percentile(sortedLatencies, 0.50),
                percentile(sortedLatencies, 0.95),
                percentile(sortedLatencies, 0.99));
    }

    private void await(CountDownLatch latch, ConcurrentLinkedQueue<Throwable> failures) {
        try {
            latch.await();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            failures.add(interruptedException);
        }
    }

    private long percentile(List<Long> sortedLatencies, double percentile) {
        if (sortedLatencies.isEmpty()) {
            return 0L;
        }
        int index = Math.min(sortedLatencies.size() - 1, (int) Math.ceil(sortedLatencies.size() * percentile) - 1);
        return sortedLatencies.get(index);
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

    private record ConcurrentBenchmarkResult(
            long matchOperations,
            long updateOperations,
            long operationsPerSecond,
            long p50Nanos,
            long p95Nanos,
            long p99Nanos) {
    }
}
