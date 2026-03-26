package io.github.vxmqmqtt.vxmq.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                new SessionOpenRequest(true, false, null, "connection-1"));

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
                new SessionOpenRequest(false, true, null, "connection-1"));

        assertFalse(result.sessionPresent());
        assertTrue(result.session().persistent());
        assertNull(result.session().sessionExpiryIntervalSeconds());
    }

    // Verifies that reopening without fresh-start restores the existing session and keeps prior subscriptions.
    @Test
    void shouldRestoreExistingPersistentSession() {
        SessionOpenResult firstOpen = sessionRegistry.openSession(
                "restored-client",
                new SessionOpenRequest(false, true, null, "connection-1"));
        firstOpen.session().subscriptions().add("sensors/+/temperature");
        sessionRegistry.onConnectionClosed("restored-client", "connection-1");

        SessionOpenResult secondOpen = sessionRegistry.openSession(
                "restored-client",
                new SessionOpenRequest(false, true, null, "connection-2"));

        assertTrue(secondOpen.sessionPresent());
        assertEquals("connection-2", secondOpen.session().connectionId());
        assertTrue(secondOpen.session().subscriptions().contains("sensors/+/temperature"));
    }

    // Verifies that zero expiry removes the session as soon as the owning connection closes.
    @Test
    void shouldDeleteSessionImmediatelyWhenExpiryIsZero() {
        sessionRegistry.openSession(
                "mqtt5-ephemeral",
                new SessionOpenRequest(false, false, 0L, "connection-1"));

        sessionRegistry.onConnectionClosed("mqtt5-ephemeral", "connection-1");

        assertTrue(sessionRegistry.find("mqtt5-ephemeral").isEmpty());
    }

    // Verifies that positive expiry keeps an offline session and records the expiration deadline.
    @Test
    void shouldKeepOfflineSessionWhenExpiryIsPositive() {
        sessionRegistry.openSession(
                "mqtt5-persistent",
                new SessionOpenRequest(false, true, 30L, "connection-1"));

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
                new SessionOpenRequest(false, true, 30L, "connection-1"));

        sessionRegistry.onConnectionClosed("expired-client", "connection-1");
        ClientSession session = sessionRegistry.find("expired-client").orElseThrow();
        session.markOffline(Instant.now().minusSeconds(1));

        assertTrue(sessionRegistry.find("expired-client").isEmpty());
    }
}
