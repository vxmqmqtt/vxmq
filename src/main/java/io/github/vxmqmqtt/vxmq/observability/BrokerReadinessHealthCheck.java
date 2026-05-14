package io.github.vxmqmqtt.vxmq.observability;

import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Reports whether the MQTT broker is ready to accept client traffic.
 */
@Readiness
@ApplicationScoped
public class BrokerReadinessHealthCheck implements HealthCheck {

    private final BrokerRuntimeState brokerRuntimeState;
    private final ClientConnectionRegistry connectionRegistry;

    @Inject
    public BrokerReadinessHealthCheck(
            BrokerRuntimeState brokerRuntimeState,
            ClientConnectionRegistry connectionRegistry) {
        this.brokerRuntimeState = brokerRuntimeState;
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public HealthCheckResponse call() {
        BrokerRuntimeSnapshot snapshot = brokerRuntimeState.snapshot();
        return BrokerHealthData.addTo(
                        HealthCheckResponse.named("vxmq-broker-readiness").status(snapshot.ready()),
                        snapshot,
                        connectionRegistry)
                .build();
    }
}
