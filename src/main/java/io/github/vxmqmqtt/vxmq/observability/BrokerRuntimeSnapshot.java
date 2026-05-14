package io.github.vxmqmqtt.vxmq.observability;

import java.time.Instant;
import java.util.Optional;

/**
 * Immutable broker runtime view shared by health checks and later observability surfaces.
 */
public record BrokerRuntimeSnapshot(
        boolean brokerEnabled,
        BrokerTransportState transportState,
        String host,
        int configuredPort,
        int actualPort,
        Optional<String> failureMessage,
        Instant lastTransitionAt) {

    public BrokerRuntimeSnapshot {
        if (transportState == null) {
            throw new IllegalArgumentException("transportState is required");
        }
        host = host == null ? "" : host;
        failureMessage = failureMessage == null ? Optional.empty() : failureMessage;
        lastTransitionAt = lastTransitionAt == null ? Instant.EPOCH : lastTransitionAt;
    }

    public boolean ready() {
        return brokerEnabled && transportState == BrokerTransportState.RUNNING && actualPort > 0;
    }

    public boolean live() {
        return transportState != BrokerTransportState.FAILED;
    }

}
