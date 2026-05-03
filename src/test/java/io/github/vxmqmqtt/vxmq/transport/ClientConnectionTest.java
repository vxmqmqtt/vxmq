package io.github.vxmqmqtt.vxmq.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for broker-side connection lifecycle state.
 */
class ClientConnectionTest {

    // Verifies that the normal MQTT transport lifecycle remains accepted.
    @Test
    void shouldAllowNormalLifecycleTransitions() {
        ClientConnection connection = connection();

        connection.transitionTo(ConnectionState.CONNECTING);
        connection.transitionTo(ConnectionState.CONNECTED);
        connection.transitionTo(ConnectionState.DISCONNECTING);
        connection.transitionTo(ConnectionState.CLOSED);

        assertEquals(ConnectionState.CLOSED, connection.state());
    }

    // Verifies that registry/protocol double-close paths can remain idempotent.
    @Test
    void shouldAllowIdempotentClosedTransition() {
        ClientConnection connection = connection();

        connection.transitionTo(ConnectionState.CONNECTING);
        connection.transitionTo(ConnectionState.CLOSED);
        connection.transitionTo(ConnectionState.CLOSED);

        assertEquals(ConnectionState.CLOSED, connection.state());
    }

    // Verifies that lifecycle regressions fail fast instead of reopening a closed connection object.
    @Test
    void shouldRejectTransitionFromClosedToConnected() {
        ClientConnection connection = connection();
        connection.transitionTo(ConnectionState.CONNECTING);
        connection.transitionTo(ConnectionState.CLOSED);

        assertThrows(IllegalStateException.class, () -> connection.transitionTo(ConnectionState.CONNECTED));
        assertEquals(ConnectionState.CLOSED, connection.state());
    }

    // Verifies that explicit disconnect cannot return to connected state on the same connection object.
    @Test
    void shouldRejectTransitionFromDisconnectingToConnected() {
        ClientConnection connection = connection();
        connection.transitionTo(ConnectionState.CONNECTING);
        connection.transitionTo(ConnectionState.CONNECTED);
        connection.transitionTo(ConnectionState.DISCONNECTING);

        assertThrows(IllegalStateException.class, () -> connection.transitionTo(ConnectionState.CONNECTED));
        assertEquals(ConnectionState.DISCONNECTING, connection.state());
    }

    private static ClientConnection connection() {
        return new ClientConnection("connection-1", "127.0.0.1", "client-1", "MQTT", 5, true);
    }
}
