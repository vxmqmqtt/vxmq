package io.github.vxmqmqtt.vxmq.transport;

/**
 * Lifecycle states for a broker-side client connection.
 */
public enum ConnectionState {
    NEW,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    CLOSED
}
