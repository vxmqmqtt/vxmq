package io.github.vxmqmqtt.vxmq.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        sessionRegistry = new InMemorySessionRegistry(2);
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
                new SessionOpenRequest(false, true, null, "connection-1", null, 1));

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
        assertTrue(sessionRegistry.createInflightMessage(
                "inflight-client",
                "sensors/room-1/temperature",
                "blocked".getBytes(),
                MqttQoS.AT_LEAST_ONCE,
                false,
                false,
                false).isEmpty());
        sessionRegistry.enqueuePendingOutboundMessage("inflight-client", new QueuedMessage(
                "sensors/room-1/temperature",
                "pending".getBytes(),
                MqttQoS.AT_LEAST_ONCE,
                false,
                false));
        assertTrue(sessionRegistry.acknowledge("inflight-client", inflightMessage.packetId()));
        assertEquals(1, sessionRegistry.drainPendingOutboundMessages("inflight-client", Instant.now()).size());
        assertEquals(1, sessionRegistry.find("inflight-client").orElseThrow().inflightMessageCount());
    }

    // Verifies that inbound QoS 2 publishes are tracked by publisher packet id until PUBREL.
    @Test
    void shouldTrackAndCompleteInboundQos2Transaction() {
        sessionRegistry.openSession(
                "qos2-publisher",
                new SessionOpenRequest(false, true, null, "connection-1", null));

        InboundQos2Message first = sessionRegistry.startInboundQos2Message(
                        "qos2-publisher",
                        9,
                        "sensors/room-1/temperature",
                        "first".getBytes(),
                        false,
                        false)
                .orElseThrow();
        InboundQos2Message duplicate = sessionRegistry.startInboundQos2Message(
                        "qos2-publisher",
                        9,
                        "sensors/room-1/temperature",
                        "second".getBytes(),
                        false,
                        true)
                .orElseThrow();

        assertEquals("first", new String(first.payloadCopy()));
        assertEquals("first", new String(duplicate.payloadCopy()));
        assertEquals(1, sessionRegistry.find("qos2-publisher").orElseThrow().inboundQos2MessageCount());
        assertEquals(first, sessionRegistry.completeInboundQos2Message("qos2-publisher", 9).orElseThrow());
        assertEquals(0, sessionRegistry.find("qos2-publisher").orElseThrow().inboundQos2MessageCount());
    }

    // Verifies that outbound QoS 2 deliveries move from PUBLISH_SENT to PUBREL_SENT and clear on PUBCOMP.
    @Test
    void shouldAdvanceOutboundQos2InflightState() {
        sessionRegistry.openSession(
                "qos2-subscriber",
                new SessionOpenRequest(false, true, null, "connection-1", null));

        InflightMessage inflightMessage = sessionRegistry.createInflightMessage(
                        "qos2-subscriber",
                        "sensors/room-1/temperature",
                        "payload".getBytes(),
                        MqttQoS.EXACTLY_ONCE,
                        false,
                        false,
                        false)
                .orElseThrow();

        assertEquals(OutboundQos2State.PUBLISH_SENT, inflightMessage.qos2State());
        InflightMessage afterPubRec = sessionRegistry.markOutboundQos2PubRec(
                        "qos2-subscriber",
                        inflightMessage.packetId())
                .orElseThrow();

        assertEquals(OutboundQos2State.PUBREL_SENT, afterPubRec.qos2State());
        assertTrue(sessionRegistry.completeOutboundQos2("qos2-subscriber", inflightMessage.packetId()));
        assertEquals(0, sessionRegistry.find("qos2-subscriber").orElseThrow().inflightMessageCount());
    }

    // Verifies that persistent closes keep outbound QoS 2 stage while requeueing only QoS 1 messages.
    @Test
    void shouldKeepOutboundQos2InflightAcrossPersistentClose() {
        sessionRegistry.openSession(
                "persistent-qos2",
                new SessionOpenRequest(false, true, null, "connection-1", null));
        InflightMessage qos2 = sessionRegistry.createInflightMessage(
                        "persistent-qos2",
                        "sensors/room-1/temperature",
                        "payload".getBytes(),
                        MqttQoS.EXACTLY_ONCE,
                        false,
                        false,
                        false)
                .orElseThrow();
        sessionRegistry.markOutboundQos2PubRec("persistent-qos2", qos2.packetId());

        sessionRegistry.onConnectionClosed("persistent-qos2", "connection-1");

        ClientSession session = sessionRegistry.find("persistent-qos2").orElseThrow();
        assertEquals(1, session.inflightMessageCount());
        assertEquals(0, session.queuedMessageCount());
        InflightMessage resumed = sessionRegistry.outboundQos2InflightMessages("persistent-qos2").getFirst();
        assertTrue(resumed.duplicate());
        assertEquals(OutboundQos2State.PUBREL_SENT, resumed.qos2State());
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
}
