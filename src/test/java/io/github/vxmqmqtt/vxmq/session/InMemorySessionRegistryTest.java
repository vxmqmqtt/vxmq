package io.github.vxmqmqtt.vxmq.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vxmqmqtt.vxmq.config.BrokerRuntimeConfig;
import io.netty.handler.codec.mqtt.MqttQoS;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the in-memory session registry semantics used by the first M2 phase.
 */
class InMemorySessionRegistryTest {

    private InMemorySessionRegistry sessionRegistry;

    @BeforeEach
    void setUp() {
        sessionRegistry = new InMemorySessionRegistry();
    }

    // Verifies that opening a non-persistent session creates a new online session without session-present.
    @Test
    void shouldCreateNewNonPersistentSession() {
        SessionOpenResult result = sessionRegistry.openSession(
                "ephemeral-client",
                new SessionOpenRequest(true, false, null, "connection-1", null));

        assertFalse(result.sessionPresent());
        assertEquals("connection-1", result.session().connectionId());
        assertFalse(result.session().persistent());
        assertNull(result.session().expiresAt());
    }

    // Verifies that opening a persistent session stores the persistence policy on the session.
    @Test
    void shouldCreatePersistentSession() {
        SessionOpenResult result = sessionRegistry.openSession(
                "persistent-client",
                new SessionOpenRequest(false, true, null, "connection-1", null));

        assertFalse(result.sessionPresent());
        assertTrue(result.session().persistent());
        assertNull(result.session().sessionExpiryIntervalSeconds());
    }

    // Verifies that reopening without fresh-start restores the existing session and keeps prior subscriptions.
    @Test
    void shouldRestoreExistingPersistentSession() {
        SessionOpenResult firstOpen = sessionRegistry.openSession(
                "restored-client",
                new SessionOpenRequest(false, true, null, "connection-1", null));
        sessionRegistry.addSubscription("restored-client", "sensors/+/temperature", MqttQoS.AT_MOST_ONCE);
        sessionRegistry.onConnectionClosed("restored-client", "connection-1");

        SessionOpenResult secondOpen = sessionRegistry.openSession(
                "restored-client",
                new SessionOpenRequest(false, true, null, "connection-2", null));

        assertTrue(secondOpen.sessionPresent());
        assertEquals("connection-2", secondOpen.session().connectionId());
        assertTrue(secondOpen.session().subscriptions().contains("sensors/+/temperature"));
    }

    // Verifies that zero expiry removes the session as soon as the owning connection closes.
    @Test
    void shouldDeleteSessionImmediatelyWhenExpiryIsZero() {
        sessionRegistry.openSession(
                "mqtt5-ephemeral",
                new SessionOpenRequest(false, false, 0L, "connection-1", null));

        sessionRegistry.onConnectionClosed("mqtt5-ephemeral", "connection-1");

        assertTrue(sessionRegistry.find("mqtt5-ephemeral").isEmpty());
    }

    // Verifies that positive expiry keeps an offline session and records the expiration deadline.
    @Test
    void shouldKeepOfflineSessionWhenExpiryIsPositive() {
        sessionRegistry.openSession(
                "mqtt5-persistent",
                new SessionOpenRequest(false, true, 30L, "connection-1", null));

        sessionRegistry.onConnectionClosed("mqtt5-persistent", "connection-1");

        ClientSession session = sessionRegistry.find("mqtt5-persistent").orElseThrow();
        assertNull(session.connectionId());
        assertEquals(30L, session.sessionExpiryIntervalSeconds());
        assertNotNull(session.expiresAt());
        assertTrue(session.expiresAt().isAfter(Instant.now()));
    }

    // Verifies that expired offline sessions are lazily removed on the next lookup.
    @Test
    void shouldLazilyPurgeExpiredSessionOnLookup() {
        sessionRegistry.openSession(
                "expired-client",
                new SessionOpenRequest(false, true, 30L, "connection-1", null));

        sessionRegistry.onConnectionClosed("expired-client", "connection-1");
        ClientSession session = sessionRegistry.find("expired-client").orElseThrow();
        session.markOffline(Instant.now().minusSeconds(1));

        assertTrue(sessionRegistry.find("expired-client").isEmpty());
    }

    // Verifies that the registry drops the oldest offline message when the queue capacity is exceeded.
    @Test
    void shouldDropOldestOfflineMessageWhenQueueIsFull() {
        sessionRegistry.configure(new TestBrokerRuntimeConfig(2));
        sessionRegistry.openSession(
                "offline-client",
                new SessionOpenRequest(false, true, null, "connection-1", null));

        sessionRegistry.enqueueOfflineMessage("offline-client", new QueuedMessage(
                "sensors/room-1/temperature",
                "first".getBytes(),
                MqttQoS.AT_LEAST_ONCE,
                false,
                false));
        sessionRegistry.enqueueOfflineMessage("offline-client", new QueuedMessage(
                "sensors/room-1/temperature",
                "second".getBytes(),
                MqttQoS.AT_LEAST_ONCE,
                false,
                false));
        sessionRegistry.enqueueOfflineMessage("offline-client", new QueuedMessage(
                "sensors/room-1/temperature",
                "third".getBytes(),
                MqttQoS.AT_LEAST_ONCE,
                false,
                false));

        ClientSession session = sessionRegistry.find("offline-client").orElseThrow();
        assertEquals(2, session.queuedMessageCount());
        assertEquals("second", new String(session.queuedMessages().get(0).payloadCopy()));
        assertEquals("third", new String(session.queuedMessages().get(1).payloadCopy()));
    }

    // Verifies that the registry can track an inflight QoS 1 delivery and clear it after PUBACK.
    @Test
    void shouldCreateAndAcknowledgeInflightDelivery() {
        sessionRegistry.openSession(
                "inflight-client",
                new SessionOpenRequest(false, true, null, "connection-1", null));

        InflightMessage inflightMessage = sessionRegistry.createInflightMessage(
                        "inflight-client",
                        "sensors/room-1/temperature",
                        "payload".getBytes(),
                        MqttQoS.AT_LEAST_ONCE,
                        false,
                        false,
                        false)
                .orElseThrow();

        assertEquals(1, sessionRegistry.find("inflight-client").orElseThrow().inflightMessageCount());
        assertTrue(sessionRegistry.acknowledge("inflight-client", inflightMessage.packetId()));
        assertEquals(0, sessionRegistry.find("inflight-client").orElseThrow().inflightMessageCount());
    }

    // Verifies that persistent sessions move unacknowledged inflight deliveries back to the offline queue on close.
    @Test
    void shouldRequeueInflightMessagesWhenPersistentConnectionCloses() {
        sessionRegistry.openSession(
                "persistent-inflight",
                new SessionOpenRequest(false, true, null, "connection-1", null));
        sessionRegistry.createInflightMessage(
                "persistent-inflight",
                "sensors/room-1/temperature",
                "payload".getBytes(),
                MqttQoS.AT_LEAST_ONCE,
                false,
                false,
                false);

        sessionRegistry.onConnectionClosed("persistent-inflight", "connection-1");

        ClientSession session = sessionRegistry.find("persistent-inflight").orElseThrow();
        assertEquals(0, session.inflightMessageCount());
        assertEquals(1, session.queuedMessageCount());
        assertTrue(session.queuedMessages().getFirst().duplicate());
    }

    private record TestBrokerRuntimeConfig(int offlineQueueCapacityPerSession) implements BrokerRuntimeConfig {

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public String host() {
            return "127.0.0.1";
        }

        @Override
        public int port() {
            return 1883;
        }

        @Override
        public int maxMessageSize() {
            return 268435455;
        }

        @Override
        public int timeoutOnConnectSeconds() {
            return 10;
        }
    }
}
