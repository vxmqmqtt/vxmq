package io.github.vxmqmqtt.vxmq.session;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory representation of the broker session state tracked for one client.
 */
public final class ClientSession {

    private final String clientId;
    private volatile String connectionId;
    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();

    public ClientSession(String clientId) {
        this.clientId = clientId;
    }

    /**
     * Returns the client identifier that owns this session.
     */
    public String clientId() {
        return clientId;
    }

    /**
     * Returns the currently bound connection id, or {@code null} if offline.
     */
    public String connectionId() {
        return connectionId;
    }

    /**
     * Binds the session to the active transport connection.
     */
    public void bindConnection(String newConnectionId) {
        this.connectionId = newConnectionId;
    }

    /**
     * Marks the session as not currently attached to a live connection.
     */
    public void unbindConnection() {
        this.connectionId = null;
    }

    /**
     * Returns the mutable subscription set for this session.
     */
    public Set<String> subscriptions() {
        return subscriptions;
    }
}
