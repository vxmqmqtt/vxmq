package io.github.vxmqmqtt.vxmq.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vxmqmqtt.vxmq.protocol.model.WillMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for will state stored inside the in-memory session registry.
 */
class InMemorySessionRegistryWillTest {

    // Verifies that opening a session with a will stores the will on the resulting session.
    @Test
    void shouldStoreWillMessageOnOpenedSession() {
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();

        SessionOpenResult openResult = sessionRegistry.openSession(
                "client-will",
                new SessionOpenRequest(
                        true,
                        true,
                        60L,
                        "connection-1",
                        new WillMessage("status/client-will", "offline".getBytes(), MqttQoS.AT_LEAST_ONCE, true)));

        assertEquals("status/client-will", openResult.session().willMessage().topicName());
        assertEquals(MqttQoS.AT_LEAST_ONCE, openResult.session().willMessage().qos());
    }

    // Verifies that opening a fresh session without a will removes any older stored will state.
    @Test
    void shouldClearPreviousWillWhenFreshSessionStartsWithoutWill() {
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        sessionRegistry.openSession(
                "client-will",
                new SessionOpenRequest(
                        true,
                        true,
                        60L,
                        "connection-1",
                        new WillMessage("status/client-will", "offline".getBytes(), MqttQoS.AT_LEAST_ONCE, false)));

        SessionOpenResult openResult = sessionRegistry.openSession(
                "client-will",
                new SessionOpenRequest(
                        true,
                        true,
                        60L,
                        "connection-2",
                        null));

        assertNull(openResult.session().willMessage());
    }

    // Verifies that discarding the will for a live connection clears the stored will state.
    @Test
    void shouldDiscardWillMessageForMatchingConnection() {
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        sessionRegistry.openSession(
                "client-will",
                new SessionOpenRequest(
                        true,
                        true,
                        60L,
                        "connection-1",
                        new WillMessage("status/client-will", "offline".getBytes(), MqttQoS.AT_LEAST_ONCE, false)));

        sessionRegistry.discardWillMessage("client-will", "connection-1");

        assertTrue(sessionRegistry.find("client-will").isPresent());
        assertNull(sessionRegistry.find("client-will").orElseThrow().willMessage());
    }
}
