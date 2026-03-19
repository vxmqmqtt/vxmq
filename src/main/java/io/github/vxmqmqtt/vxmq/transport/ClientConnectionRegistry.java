package io.github.vxmqmqtt.vxmq.transport;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ClientConnectionRegistry {

    private final Map<String, ClientConnection> connections = new ConcurrentHashMap<>();
    private final Map<String, String> activeConnectionIdsByClientId = new ConcurrentHashMap<>();

    public ClientConnection open(
            String remoteAddress,
            String requestedClientId,
            String protocolName,
            int protocolVersion,
            boolean cleanSession) {
        ClientConnection connection = new ClientConnection(
                UUID.randomUUID().toString(),
                remoteAddress,
                requestedClientId,
                protocolName,
                protocolVersion,
                cleanSession);
        connection.transitionTo(ConnectionState.CONNECTING);
        connections.put(connection.internalId(), connection);
        return connection;
    }

    public Optional<ClientConnection> find(String internalId) {
        return Optional.ofNullable(connections.get(internalId));
    }

    public Optional<String> bindClientId(String clientId, String connectionId) {
        String previousConnectionId = activeConnectionIdsByClientId.put(clientId, connectionId);
        if (previousConnectionId == null || previousConnectionId.equals(connectionId)) {
            return Optional.empty();
        }
        return Optional.of(previousConnectionId);
    }

    public Optional<String> findActiveConnectionId(String clientId) {
        return Optional.ofNullable(activeConnectionIdsByClientId.get(clientId));
    }

    public void close(String internalId) {
        ClientConnection connection = connections.remove(internalId);
        if (connection != null) {
            String effectiveClientId = connection.effectiveClientId();
            if (effectiveClientId != null) {
                activeConnectionIdsByClientId.remove(effectiveClientId, internalId);
            }
            connection.transitionTo(ConnectionState.CLOSED);
        }
    }

    public Collection<ClientConnection> all() {
        return connections.values();
    }
}
