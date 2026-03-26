package io.github.vxmqmqtt.vxmq.session;

import java.util.Optional;

/**
 * Stores session state independently from active transport connections.
 */
public interface SessionRegistry {

    /**
     * Opens a session for the supplied client, either by restoring an existing one or creating a new one.
     */
    SessionOpenResult openSession(String clientId, SessionOpenRequest request);

    /**
     * Applies disconnect/close semantics for the supplied connection owner.
     */
    Optional<ClientSession> onConnectionClosed(String clientId, String connectionId);

    /**
     * Deletes the complete session state for the client if it exists.
     */
    Optional<ClientSession> removeSession(String clientId);

    /**
     * Adds a topic filter to the client's session state.
     */
    void addSubscription(String clientId, String topicFilter);

    /**
     * Removes a topic filter from the client's session state.
     */
    boolean removeSubscription(String clientId, String topicFilter);

    /**
     * Returns the session for a client if it exists.
     */
    Optional<ClientSession> find(String clientId);
}
