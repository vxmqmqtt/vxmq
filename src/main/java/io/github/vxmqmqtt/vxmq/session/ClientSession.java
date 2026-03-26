package io.github.vxmqmqtt.vxmq.session;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory representation of the broker session state tracked for one client.
 */
public final class ClientSession {

    private final String clientId;
    private volatile String connectionId;
    private volatile boolean persistent;
    private volatile Long sessionExpiryIntervalSeconds;
    private volatile Instant expiresAt;
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
     * Returns whether the session should survive a future connection close.
     */
    public boolean persistent() {
        return persistent;
    }

    /**
     * Returns the MQTT 5 session expiry interval, or {@code null} for MQTT 3.1.1 persistent sessions.
     */
    public Long sessionExpiryIntervalSeconds() {
        return sessionExpiryIntervalSeconds;
    }

    /**
     * Returns the time when the offline session should expire, or {@code null} while online or indefinite.
     */
    public Instant expiresAt() {
        return expiresAt;
    }

    /**
     * Activates the session for a live transport connection and applies the current connect-time policy.
     */
    public void activate(String newConnectionId, boolean newPersistent, Long newSessionExpiryIntervalSeconds) {
        this.connectionId = newConnectionId;
        this.persistent = newPersistent;
        this.sessionExpiryIntervalSeconds = newSessionExpiryIntervalSeconds;
        this.expiresAt = null;
    }

    /**
     * Marks the session as offline and optionally schedules future expiry.
     */
    public void markOffline(Instant newExpiresAt) {
        this.connectionId = null;
        this.expiresAt = newExpiresAt;
    }

    /**
     * Returns the mutable subscription set for this session.
     */
    public Set<String> subscriptions() {
        return subscriptions;
    }
}
