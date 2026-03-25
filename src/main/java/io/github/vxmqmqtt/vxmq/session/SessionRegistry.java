package io.github.vxmqmqtt.vxmq.session;

import java.util.Optional;

/**
 * Stores session state independently from active transport connections.
 */
public interface SessionRegistry {

    /**
     * Creates or updates the session binding for an active client connection.
     */
    ClientSession bindConnection(String clientId, String connectionId);

    /**
     * Unbinds the session only if the supplied connection still owns it.
     */
    void unbindConnection(String clientId, String connectionId);

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
