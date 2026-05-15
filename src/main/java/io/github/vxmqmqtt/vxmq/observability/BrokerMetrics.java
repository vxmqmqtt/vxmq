package io.github.vxmqmqtt.vxmq.observability;

import io.github.vxmqmqtt.vxmq.session.SessionRegistry;
import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Registers the stable, low-cardinality broker metrics exposed through Micrometer/Prometheus.
 */
@ApplicationScoped
@Startup
public class BrokerMetrics {

    private final Counter transportStarts;
    private final Counter transportStops;
    private final Counter connectionsAccepted;
    private final Counter subscriptionsAdded;
    private final Counter subscriptionsRemoved;
    private final Counter messagesRouted;
    private final Counter messageDeliveryMatches;
    private final Counter protocolWarnings;

    @Inject
    public BrokerMetrics(
            MeterRegistry meterRegistry,
            BrokerRuntimeState brokerRuntimeState,
            ClientConnectionRegistry connectionRegistry,
            SessionRegistry sessionRegistry) {
        Gauge.builder("vxmq_connections_active", connectionRegistry, registry -> registry.all().size())
                .description("Current MQTT transport connections tracked by the broker")
                .register(meterRegistry);
        Gauge.builder("vxmq_sessions_total", sessionRegistry, SessionRegistry::sessionCount)
                .description("Current MQTT sessions retained by the broker")
                .register(meterRegistry);
        Gauge.builder("vxmq_broker_ready", brokerRuntimeState, state -> state.snapshot().ready() ? 1.0 : 0.0)
                .description("Whether the MQTT broker is ready to accept client traffic")
                .register(meterRegistry);
        Gauge.builder("vxmq_broker_live", brokerRuntimeState, state -> state.snapshot().live() ? 1.0 : 0.0)
                .description("Whether the MQTT broker runtime is live")
                .register(meterRegistry);
        for (BrokerTransportState transportState : BrokerTransportState.values()) {
            Gauge.builder("vxmq_broker_transport_state", brokerRuntimeState,
                            state -> state.snapshot().transportState() == transportState ? 1.0 : 0.0)
                    .tag("state", transportState.name())
                    .description("MQTT transport lifecycle state, exposed as one-hot state gauges")
                    .register(meterRegistry);
        }

        transportStarts = counter(meterRegistry, "vxmq_transport_starts", "MQTT transport start events");
        transportStops = counter(meterRegistry, "vxmq_transport_stops", "MQTT transport stop events");
        connectionsAccepted = counter(meterRegistry, "vxmq_connections_accepted", "Accepted MQTT connections");
        subscriptionsAdded = counter(meterRegistry, "vxmq_subscriptions_added", "MQTT subscriptions added");
        subscriptionsRemoved = counter(meterRegistry, "vxmq_subscriptions_removed", "MQTT subscriptions removed");
        messagesRouted = counter(meterRegistry, "vxmq_messages_routed", "Inbound publishes routed by the broker");
        messageDeliveryMatches = counter(
                meterRegistry,
                "vxmq_message_delivery_matches",
                "Matched online deliveries and offline queue entries produced by routed publishes");
        protocolWarnings = counter(meterRegistry, "vxmq_protocol_warnings", "Protocol warning events");
    }

    public void transportStarted() {
        transportStarts.increment();
    }

    public void transportStopped() {
        transportStops.increment();
    }

    public void connectionAccepted() {
        connectionsAccepted.increment();
    }

    public void subscriptionAdded() {
        subscriptionsAdded.increment();
    }

    public void subscriptionRemoved() {
        subscriptionsRemoved.increment();
    }

    public void messageRouted(int matchedClients) {
        messagesRouted.increment();
        if (matchedClients > 0) {
            messageDeliveryMatches.increment(matchedClients);
        }
    }

    public void protocolWarning() {
        protocolWarnings.increment();
    }

    private Counter counter(MeterRegistry meterRegistry, String name, String description) {
        return Counter.builder(name)
                .description(description)
                .register(meterRegistry);
    }
}
