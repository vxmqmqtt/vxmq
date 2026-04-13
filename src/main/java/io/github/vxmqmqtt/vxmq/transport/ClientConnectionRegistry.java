package io.github.vxmqmqtt.vxmq.transport;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks transport connections and the currently active connection per client id.
 */
@ApplicationScoped
public class ClientConnectionRegistry {

    private final Map<String, ClientConnection> connections = new ConcurrentHashMap<>();
    private final Map<String, String> activeConnectionIdsByClientId = new ConcurrentHashMap<>();

    public ClientConnection open(
            String remoteAddress,
            String requestedClientId,
            String protocolName,
            int protocolVersion,
            boolean startCleanSession) {
        ClientConnection connection = new ClientConnection(
                UUID.randomUUID().toString(),
                remoteAddress,
                requestedClientId,
                protocolName,
                protocolVersion,
                startCleanSession);
        connection.transitionTo(ConnectionState.CONNECTING);
        connections.put(connection.connectionId(), connection);
        return connection;
    }

    /**
     * Returns the connection with the supplied connection id if it is still known.
     */
    public Optional<ClientConnection> find(String connectionId) {
        return Optional.ofNullable(connections.get(connectionId));
    }

    /**
     * Binds a client id to the current connection and returns the superseded connection if any.
     */
    public Optional<String> bindClientId(String clientId, String connectionId) {
        String previousConnectionId = activeConnectionIdsByClientId.put(clientId, connectionId);
        if (previousConnectionId == null || previousConnectionId.equals(connectionId)) {
            return Optional.empty();
        }
        return Optional.of(previousConnectionId);
    }

    /**
     * Returns the active connection id currently associated with a client.
     */
    public Optional<String> findActiveConnectionId(String clientId) {
        return Optional.ofNullable(activeConnectionIdsByClientId.get(clientId));
    }

    /**
     * Removes a connection from the registry and clears any active client-id mapping it owns.
     */
    public void close(String connectionId) {
        ClientConnection connection = connections.remove(connectionId);
        if (connection != null) {
            String effectiveClientId = connection.effectiveClientId();
            if (effectiveClientId != null) {
                activeConnectionIdsByClientId.remove(effectiveClientId, connectionId);
            }
            connection.transitionTo(ConnectionState.CLOSED);
        }
    }

    /**
     * Returns all currently tracked connections.
     */
    public Collection<ClientConnection> all() {
        return connections.values();
    }
}
