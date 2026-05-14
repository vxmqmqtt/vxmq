package io.github.vxmqmqtt.vxmq.observability;

import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Reports whether the broker process should be considered alive.
 */
@Liveness
@ApplicationScoped
public class BrokerLivenessHealthCheck implements HealthCheck {

    private final BrokerRuntimeState brokerRuntimeState;
    private final ClientConnectionRegistry connectionRegistry;

    @Inject
    public BrokerLivenessHealthCheck(
            BrokerRuntimeState brokerRuntimeState,
            ClientConnectionRegistry connectionRegistry) {
        this.brokerRuntimeState = brokerRuntimeState;
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public HealthCheckResponse call() {
        BrokerRuntimeSnapshot snapshot = brokerRuntimeState.snapshot();
        return BrokerHealthData.addTo(
                        HealthCheckResponse.named("vxmq-broker-liveness").status(snapshot.live()),
                        snapshot,
                        connectionRegistry)
                .build();
    }
}
