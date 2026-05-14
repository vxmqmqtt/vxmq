package io.github.vxmqmqtt.vxmq.observability;

/**
 * Lifecycle state exposed by the MQTT transport for health checks and future metrics.
 */
public enum BrokerTransportState {
    DISABLED,
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED
}
