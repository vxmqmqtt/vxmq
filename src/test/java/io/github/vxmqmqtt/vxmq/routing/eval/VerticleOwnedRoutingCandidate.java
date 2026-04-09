package io.github.vxmqmqtt.vxmq.routing.eval;

import io.github.vxmqmqtt.vxmq.routing.DefaultMqttTopicSupport;
import io.github.vxmqmqtt.vxmq.routing.InMemorySubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Single-owner routing candidate that serializes all access through one Verticle context.
 */
final class VerticleOwnedRoutingCandidate implements RoutingRegistryCandidate {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final Vertx vertx = Vertx.vertx();
    private final OwnerVerticle verticle = new OwnerVerticle();
    private final String deploymentId;

    VerticleOwnedRoutingCandidate() {
        try {
            this.deploymentId = vertx.deployVerticle(verticle)
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deploy routing owner verticle", exception);
        }
    }

    @Override
    public String name() {
        return "verticle-owner";
    }

    @Override
    public void addSubscription(SubscriptionBinding binding) {
        invoke(() -> {
            verticle.registry().addSubscription(binding);
            return null;
        });
    }

    @Override
    public boolean removeSubscription(String clientId, String topicFilter) {
        return invoke(() -> verticle.registry().removeSubscription(clientId, topicFilter));
    }

    @Override
    public Collection<SubscriptionBinding> match(String topicName) {
        return invoke(() -> new ArrayList<>(verticle.registry().match(topicName)));
    }

    @Override
    public void close() throws Exception {
        vertx.undeploy(deploymentId).toCompletionStage().toCompletableFuture().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        vertx.close().toCompletionStage().toCompletableFuture().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    private <T> T invoke(Supplier<T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Context ownerContext = verticle.ownerContext();
        if (ownerContext == null) {
            throw new IllegalStateException("Routing owner context is not ready");
        }
        ownerContext.runOnContext(ignored -> {
            try {
                future.complete(action.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        try {
            return future.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to execute routing operation on owner verticle", exception);
        }
    }

    /**
     * Factory for the single-owner Verticle candidate.
     */
    static final class Factory implements RoutingRegistryCandidateFactory {

        @Override
        public String name() {
            return "verticle-owner";
        }

        @Override
        public RoutingRegistryCandidate create() {
            return new VerticleOwnedRoutingCandidate();
        }
    }

    private static final class OwnerVerticle extends AbstractVerticle {

        private final InMemorySubscriptionRegistry registry = new InMemorySubscriptionRegistry(new DefaultMqttTopicSupport());
        private volatile Context ownerContext;

        @Override
        public void start() {
            ownerContext = context;
        }

        InMemorySubscriptionRegistry registry() {
            return registry;
        }

        Context ownerContext() {
            return ownerContext;
        }
    }
}
