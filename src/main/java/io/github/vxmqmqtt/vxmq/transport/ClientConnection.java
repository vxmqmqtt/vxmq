package io.github.vxmqmqtt.vxmq.transport;

import io.github.vxmqmqtt.vxmq.protocol.model.WillMessage;
import java.util.Objects;

/**
 * Broker-side view of a transport connection and its negotiated MQTT identity.
 * The start-clean flag is a broker-internal abstraction: it maps to MQTT 3.1.1 Clean Session
 * and MQTT 5 Clean Start without preserving version-specific field names here.
 */
public final class ClientConnection {

    private final String connectionId;
    private final String remoteAddress;
    private final String requestedClientId;
    private final String protocolName;
    private final int protocolVersion;
    private final boolean startCleanSession;
    private volatile String effectiveClientId;
    private volatile String principal;
    private volatile WillMessage willMessage;
    private volatile ConnectionState state;

    public ClientConnection(
            String connectionId,
            String remoteAddress,
            String requestedClientId,
            String protocolName,
            int protocolVersion,
            boolean startCleanSession) {
        this.connectionId = Objects.requireNonNull(connectionId, "connectionId");
        this.remoteAddress = remoteAddress == null ? "unknown" : remoteAddress;
        this.requestedClientId = requestedClientId == null ? "" : requestedClientId;
        this.protocolName = protocolName == null ? "" : protocolName;
        this.protocolVersion = protocolVersion;
        this.startCleanSession = startCleanSession;
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

    /**
     * Returns whether this connection requested a fresh session start.
     */
    public boolean startCleanSession() {
        return startCleanSession;
    }

    public String effectiveClientId() {
        return effectiveClientId;
    }

    public String principal() {
        return principal;
    }

    /**
     * Stores the authenticated identity associated with this connection.
     */
    public void assignPrincipal(String principal) {
        this.principal = principal;
    }

    /**
     * Stores the will message negotiated for this specific live connection.
     */
    public void assignWillMessage(WillMessage willMessage) {
        this.willMessage = copyWillMessage(willMessage);
    }

    /**
     * Clears the will for this live connection without returning it.
     */
    public void clearWillMessage() {
        this.willMessage = null;
    }

    /**
     * Clears and returns the will for this live connection so it can be published only once.
     *
     * This live-connection snapshot is consumed by the transport close/disconnect event sequence. If future
     * callers consume it from multiple threads directly, replace the volatile field with synchronized or CAS
     * ownership so the one-shot guarantee remains explicit.
     */
    public WillMessage takeWillMessage() {
        WillMessage current = willMessage;
        willMessage = null;
        return copyWillMessage(current);
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
        ConnectionState target = Objects.requireNonNull(newState, "newState");
        ConnectionState current = state;
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Invalid connection state transition from %s to %s".formatted(current, target));
        }
        this.state = target;
    }

    private WillMessage copyWillMessage(WillMessage source) {
        if (source == null) {
            return null;
        }
        return new WillMessage(
                source.topicName(),
                source.payloadCopy(),
                source.qos(),
                source.retain(),
                source.properties());
    }
}
