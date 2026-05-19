package io.github.vxmqmqtt.vxmq.protocol;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vxmqmqtt.vxmq.routing.DefaultMqttTopicSupport;
import io.github.vxmqmqtt.vxmq.routing.InMemorySubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import io.github.vxmqmqtt.vxmq.session.InMemorySessionRegistry;
import io.github.vxmqmqtt.vxmq.session.SessionOpenRequest;
import io.netty.handler.codec.mqtt.MqttQoS;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionLifecycleCoordinatorTest {

    // Verifies that expired session cleanup removes routing indexes derived from the removed session truth.
    @Test
    void shouldClearRoutingBindingsForExpiredSessions() {
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        InMemorySubscriptionRegistry subscriptionRegistry =
                new InMemorySubscriptionRegistry(new DefaultMqttTopicSupport());
        SessionLifecycleCoordinator coordinator =
                new SessionLifecycleCoordinator(sessionRegistry, subscriptionRegistry);
        sessionRegistry.openSession(
                "client-a",
                new SessionOpenRequest(false, true, 1L, "connection-a", null));
        sessionRegistry.onConnectionClosed("client-a", "connection-a");
        SubscriptionBinding binding =
                new SubscriptionBinding("client-a", "sensors/+/temperature", MqttQoS.AT_LEAST_ONCE);
        sessionRegistry.addSubscription(binding);
        subscriptionRegistry.addSubscription(binding);

        coordinator.clearExpiredSessionRoutingBindings(Instant.now().plusSeconds(2));

        assertTrue(subscriptionRegistry.match("sensors/room-1/temperature").isEmpty());
    }
}
