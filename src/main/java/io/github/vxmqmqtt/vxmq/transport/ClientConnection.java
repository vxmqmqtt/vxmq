package io.github.vxmqmqtt.vxmq.transport;

import java.util.Objects;

public final class ClientConnection {

    private final String internalId;
    private final String remoteAddress;
    private final String requestedClientId;
    private final String protocolName;
    private final int protocolVersion;
    private final boolean cleanSession;
    private volatile String effectiveClientId;
    private volatile ConnectionState state;

    public ClientConnection(
            String internalId,
            String remoteAddress,
            String requestedClientId,
            String protocolName,
            int protocolVersion,
            boolean cleanSession) {
        this.internalId = Objects.requireNonNull(internalId, "internalId");
        this.remoteAddress = remoteAddress == null ? "unknown" : remoteAddress;
        this.requestedClientId = requestedClientId == null ? "" : requestedClientId;
        this.protocolName = protocolName == null ? "" : protocolName;
        this.protocolVersion = protocolVersion;
        this.cleanSession = cleanSession;
        this.state = ConnectionState.NEW;
    }

    public String internalId() {
        return internalId;
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

    public void assignClientId(String clientId) {
        this.effectiveClientId = clientId;
    }

    public ConnectionState state() {
        return state;
    }

    public void transitionTo(ConnectionState newState) {
        this.state = Objects.requireNonNull(newState, "newState");
    }
}
