package io.github.vxmqmqtt.vxmq.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import java.util.Map;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

class BrokerReadinessHealthCheckTest {

    @Test
    void shouldReportDownUntilTransportIsRunning() {
        BrokerRuntimeState state = new BrokerRuntimeState();
        BrokerReadinessHealthCheck healthCheck =
                new BrokerReadinessHealthCheck(state, new ClientConnectionRegistry());

        assertEquals(HealthCheckResponse.Status.DOWN, healthCheck.call().getStatus());

        state.markDisabled("127.0.0.1", 1883);
        assertEquals(HealthCheckResponse.Status.DOWN, healthCheck.call().getStatus());

        state.markStarting("127.0.0.1", 1883);
        assertEquals(HealthCheckResponse.Status.DOWN, healthCheck.call().getStatus());

        state.markFailed("127.0.0.1", 1883, new IllegalStateException("bind failed"));
        assertEquals(HealthCheckResponse.Status.DOWN, healthCheck.call().getStatus());
    }

    @Test
    void shouldReportUpWhenTransportIsRunningWithActualPort() {
        BrokerRuntimeState state = new BrokerRuntimeState();
        ClientConnectionRegistry connectionRegistry = new ClientConnectionRegistry();
        connectionRegistry.open("remote", "client-a", "MQTT", 5, true);
        state.markRunning("127.0.0.1", 1883, 32123);
        BrokerReadinessHealthCheck healthCheck =
                new BrokerReadinessHealthCheck(state, connectionRegistry);

        HealthCheckResponse response = healthCheck.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        Map<String, Object> data = response.getData().orElseThrow();
        assertEquals(true, data.get("broker.enabled"));
        assertEquals("RUNNING", data.get("transport.state"));
        assertEquals("127.0.0.1", data.get("transport.host"));
        assertEquals(1883L, data.get("transport.configured.port"));
        assertEquals(32123L, data.get("transport.actual.port"));
        assertEquals(1L, data.get("connections.active"));
        assertTrue(data.containsKey("transport.failure"));
    }
}
