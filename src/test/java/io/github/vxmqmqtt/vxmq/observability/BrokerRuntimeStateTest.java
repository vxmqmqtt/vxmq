package io.github.vxmqmqtt.vxmq.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BrokerRuntimeStateTest {

    @Test
    void shouldStartAsStoppedAndLiveButNotReady() {
        BrokerRuntimeState state = new BrokerRuntimeState();

        BrokerRuntimeSnapshot snapshot = state.snapshot();

        assertEquals(BrokerTransportState.STOPPED, snapshot.transportState());
        assertFalse(snapshot.brokerEnabled());
        assertFalse(snapshot.ready());
        assertTrue(snapshot.live());
    }

    @Test
    void shouldExposeDisabledBrokerAsLiveButNotReady() {
        BrokerRuntimeState state = new BrokerRuntimeState();

        state.markDisabled("127.0.0.1", 1883);

        BrokerRuntimeSnapshot snapshot = state.snapshot();
        assertEquals(BrokerTransportState.DISABLED, snapshot.transportState());
        assertFalse(snapshot.brokerEnabled());
        assertEquals("127.0.0.1", snapshot.host());
        assertEquals(1883, snapshot.configuredPort());
        assertEquals(-1, snapshot.actualPort());
        assertFalse(snapshot.ready());
        assertTrue(snapshot.live());
    }

    @Test
    void shouldExposeRunningBrokerAsReadyAndLive() {
        BrokerRuntimeState state = new BrokerRuntimeState();

        state.markStarting("127.0.0.1", 1883);
        state.markRunning("127.0.0.1", 1883, 32123);

        BrokerRuntimeSnapshot snapshot = state.snapshot();
        assertEquals(BrokerTransportState.RUNNING, snapshot.transportState());
        assertTrue(snapshot.brokerEnabled());
        assertEquals("127.0.0.1", snapshot.host());
        assertEquals(1883, snapshot.configuredPort());
        assertEquals(32123, snapshot.actualPort());
        assertFalse(snapshot.failureMessage().isPresent());
        assertTrue(snapshot.ready());
        assertTrue(snapshot.live());
    }

    @Test
    void shouldExposeFailedBrokerAsNotReadyAndNotLive() {
        BrokerRuntimeState state = new BrokerRuntimeState();

        state.markFailed("127.0.0.1", 1883, new IllegalStateException("bind failed"));

        BrokerRuntimeSnapshot snapshot = state.snapshot();
        assertEquals(BrokerTransportState.FAILED, snapshot.transportState());
        assertTrue(snapshot.brokerEnabled());
        assertEquals("bind failed", snapshot.failureMessage().orElseThrow());
        assertFalse(snapshot.ready());
        assertFalse(snapshot.live());
    }
}
