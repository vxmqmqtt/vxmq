package io.github.vxmqmqtt.vxmq.observability;

import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

final class BrokerHealthData {

    private BrokerHealthData() {
    }

    static HealthCheckResponseBuilder addTo(
            HealthCheckResponseBuilder builder,
            BrokerRuntimeSnapshot snapshot,
            ClientConnectionRegistry connectionRegistry) {
        return builder
                .withData("broker.enabled", snapshot.brokerEnabled())
                .withData("transport.state", snapshot.transportState().name())
                .withData("transport.host", snapshot.host())
                .withData("transport.configured.port", snapshot.configuredPort())
                .withData("transport.actual.port", snapshot.actualPort())
                .withData("transport.failure", snapshot.failureMessage().orElse(""))
                .withData("connections.active", connectionRegistry.all().size());
    }
}
