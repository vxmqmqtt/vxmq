package io.github.vxmqmqtt.vxmq.transport;

/**
 * Lifecycle states for a broker-side client connection.
 */
public enum ConnectionState {
    NEW,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    CLOSED;

    public boolean canTransitionTo(ConnectionState target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        return switch (this) {
            case NEW -> target == CONNECTING || target == CLOSED;
            case CONNECTING -> target == CONNECTED || target == CLOSED;
            case CONNECTED -> target == DISCONNECTING || target == CLOSED;
            case DISCONNECTING -> target == CLOSED;
            case CLOSED -> false;
        };
    }
}
