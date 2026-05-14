package io.github.vxmqmqtt.vxmq.observability;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks the broker runtime lifecycle in a form that can be reused by health, metrics, and diagnostics.
 */
@ApplicationScoped
public class BrokerRuntimeState {

    private final AtomicReference<BrokerRuntimeSnapshot> snapshot = new AtomicReference<>(
            new BrokerRuntimeSnapshot(false, BrokerTransportState.STOPPED, "", -1, -1, Optional.empty(), Instant.now()));

    public BrokerRuntimeSnapshot snapshot() {
        return snapshot.get();
    }

    public void markDisabled(String host, int configuredPort) {
        transition(false, BrokerTransportState.DISABLED, host, configuredPort, -1, null);
    }

    public void markStarting(String host, int configuredPort) {
        transition(true, BrokerTransportState.STARTING, host, configuredPort, -1, null);
    }

    public void markRunning(String host, int configuredPort, int actualPort) {
        transition(true, BrokerTransportState.RUNNING, host, configuredPort, actualPort, null);
    }

    public void markStopping(String host, int configuredPort, int actualPort) {
        transition(true, BrokerTransportState.STOPPING, host, configuredPort, actualPort, null);
    }

    public void markStopped(String host, int configuredPort) {
        transition(false, BrokerTransportState.STOPPED, host, configuredPort, -1, null);
    }

    public void markFailed(String host, int configuredPort, Throwable failure) {
        transition(true, BrokerTransportState.FAILED, host, configuredPort, -1, failureMessage(failure));
    }

    private void transition(
            boolean brokerEnabled,
            BrokerTransportState transportState,
            String host,
            int configuredPort,
            int actualPort,
            String failureMessage) {
        snapshot.set(new BrokerRuntimeSnapshot(
                brokerEnabled,
                transportState,
                host,
                configuredPort,
                actualPort,
                Optional.ofNullable(failureMessage).filter(message -> !message.isBlank()),
                Instant.now()));
    }

    private String failureMessage(Throwable failure) {
        if (failure == null) {
            return null;
        }
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
