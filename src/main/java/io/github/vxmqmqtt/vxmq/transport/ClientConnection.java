package io.github.vxmqmqtt.vxmq.transport;

import java.util.Objects;

/**
 * Broker-side view of a transport connection and its negotiated MQTT identity.
 */
public final class ClientConnection {

    private final String connectionId;
    private final String remoteAddress;
    private final String requestedClientId;
    private final String protocolName;
    private final int protocolVersion;
    private final boolean cleanSession;
    private volatile String effectiveClientId;
    private volatile ConnectionState state;

    public ClientConnection(
            String connectionId,
            String remoteAddress,
            String requestedClientId,
            String protocolName,
            int protocolVersion,
            boolean cleanSession) {
        this.connectionId = Objects.requireNonNull(connectionId, "connectionId");
        this.remoteAddress = remoteAddress == null ? "unknown" : remoteAddress;
        this.requestedClientId = requestedClientId == null ? "" : requestedClientId;
        this.protocolName = protocolName == null ? "" : protocolName;
        this.protocolVersion = protocolVersion;
        this.cleanSession = cleanSession;
        this.state = ConnectionState.NEW;
    }

    /**
     * Returns the broker-generated connection identifier.
     */
    public String connectionId() {
        return connectionId;
    }

    public String remoteAddress() {
        return remoteAddress;
    }

    public String requestedClientId() {
        return requestedClientId;
    }

    public String protocolName() {
        return protocolName;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public boolean cleanSession() {
        return cleanSession;
    }

    public String effectiveClientId() {
        return effectiveClientId;
    }

    /**
     * Stores the final client identifier assigned after CONNECT validation.
     */
    public void assignClientId(String clientId) {
        this.effectiveClientId = clientId;
    }

    public ConnectionState state() {
        return state;
    }

    /**
     * Records the current lifecycle state of the transport connection.
     */
    public void transitionTo(ConnectionState newState) {
        this.state = Objects.requireNonNull(newState, "newState");
    }
}
