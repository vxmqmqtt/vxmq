package io.github.vxmqmqtt.vxmq.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

class BrokerLivenessHealthCheckTest {

    @Test
    void shouldReportUpUnlessTransportHasFailed() {
        BrokerRuntimeState state = new BrokerRuntimeState();
        BrokerLivenessHealthCheck healthCheck =
                new BrokerLivenessHealthCheck(state, new ClientConnectionRegistry());

        assertEquals(HealthCheckResponse.Status.UP, healthCheck.call().getStatus());

        state.markDisabled("127.0.0.1", 1883);
        assertEquals(HealthCheckResponse.Status.UP, healthCheck.call().getStatus());

        state.markStarting("127.0.0.1", 1883);
        assertEquals(HealthCheckResponse.Status.UP, healthCheck.call().getStatus());

        state.markRunning("127.0.0.1", 1883, 32123);
        assertEquals(HealthCheckResponse.Status.UP, healthCheck.call().getStatus());

        state.markStopping("127.0.0.1", 1883, 32123);
        assertEquals(HealthCheckResponse.Status.UP, healthCheck.call().getStatus());

        state.markFailed("127.0.0.1", 1883, new IllegalStateException("bind failed"));
        assertEquals(HealthCheckResponse.Status.DOWN, healthCheck.call().getStatus());
    }
}
