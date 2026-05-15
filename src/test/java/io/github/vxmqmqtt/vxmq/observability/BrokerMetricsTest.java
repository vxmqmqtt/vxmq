package io.github.vxmqmqtt.vxmq.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.vxmqmqtt.vxmq.protocol.model.WillMessage;
import io.github.vxmqmqtt.vxmq.session.InMemorySessionRegistry;
import io.github.vxmqmqtt.vxmq.session.SessionOpenRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class BrokerMetricsTest {

    @Test
    void shouldExposeRuntimeConnectionAndSessionGauges() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BrokerRuntimeState runtimeState = new BrokerRuntimeState();
        ClientConnectionRegistry connectionRegistry = new ClientConnectionRegistry();
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();

        new BrokerMetrics(meterRegistry, runtimeState, connectionRegistry, sessionRegistry);

        assertEquals(0.0, gauge(meterRegistry, "vxmq_connections_active"));
        assertEquals(0.0, gauge(meterRegistry, "vxmq_sessions_total"));
        assertEquals(0.0, gauge(meterRegistry, "vxmq_broker_ready"));
        assertEquals(1.0, gauge(meterRegistry, "vxmq_broker_live"));
        assertTransportState(meterRegistry, BrokerTransportState.STOPPED);

        connectionRegistry.open("remote", "client-a", "MQTT", 5, true);
        sessionRegistry.openSession("client-a", new SessionOpenRequest(
                false,
                true,
                null,
                "connection-1",
                (WillMessage) null,
                65_535,
                268_435_455));
        runtimeState.markRunning("127.0.0.1", 1883, 32123);

        assertEquals(1.0, gauge(meterRegistry, "vxmq_connections_active"));
        assertEquals(1.0, gauge(meterRegistry, "vxmq_sessions_total"));
        assertEquals(1.0, gauge(meterRegistry, "vxmq_broker_ready"));
        assertEquals(1.0, gauge(meterRegistry, "vxmq_broker_live"));
        assertTransportState(meterRegistry, BrokerTransportState.RUNNING);
    }

    @Test
    void shouldCountBrokerEvents() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BrokerMetrics metrics = new BrokerMetrics(
                meterRegistry,
                new BrokerRuntimeState(),
                new ClientConnectionRegistry(),
                new InMemorySessionRegistry());

        metrics.transportStarted();
        metrics.transportStopped();
        metrics.connectionAccepted();
        metrics.subscriptionAdded();
        metrics.subscriptionRemoved();
        metrics.messageRouted(0);
        metrics.messageRouted(-1);
        metrics.messageRouted(3);
        metrics.protocolWarning();

        assertEquals(1.0, counter(meterRegistry, "vxmq_transport_starts"));
        assertEquals(1.0, counter(meterRegistry, "vxmq_transport_stops"));
        assertEquals(1.0, counter(meterRegistry, "vxmq_connections_accepted"));
        assertEquals(1.0, counter(meterRegistry, "vxmq_subscriptions_added"));
        assertEquals(1.0, counter(meterRegistry, "vxmq_subscriptions_removed"));
        assertEquals(3.0, counter(meterRegistry, "vxmq_messages_routed"));
        assertEquals(3.0, counter(meterRegistry, "vxmq_message_delivery_matches"));
        assertEquals(1.0, counter(meterRegistry, "vxmq_protocol_warnings"));
    }

    private static double gauge(SimpleMeterRegistry meterRegistry, String name) {
        return meterRegistry.get(name).gauge().value();
    }

    private static double gauge(SimpleMeterRegistry meterRegistry, String name, String tagKey, String tagValue) {
        return meterRegistry.get(name).tag(tagKey, tagValue).gauge().value();
    }

    private static double counter(SimpleMeterRegistry meterRegistry, String name) {
        return meterRegistry.get(name).counter().count();
    }

    private static void assertTransportState(SimpleMeterRegistry meterRegistry, BrokerTransportState expectedState) {
        for (BrokerTransportState state : BrokerTransportState.values()) {
            assertEquals(
                    state == expectedState ? 1.0 : 0.0,
                    gauge(meterRegistry, "vxmq_broker_transport_state", "state", state.name()));
        }
    }
}
