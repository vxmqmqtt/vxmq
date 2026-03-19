package io.github.vxmqmqtt.vxmq.session;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InMemorySessionRegistry implements SessionRegistry {

    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();

    @Override
    public ClientSession bindConnection(String clientId, String connectionId) {
        ClientSession session = sessions.computeIfAbsent(clientId, ClientSession::new);
        session.bindConnection(connectionId);
        return session;
    }

    @Override
    public void unbindConnection(String clientId, String connectionId) {
        ClientSession session = sessions.get(clientId);
        if (session != null && connectionId.equals(session.connectionId())) {
            session.unbindConnection();
        }
    }

    @Override
    public void addSubscription(String clientId, String topicFilter) {
        sessions.computeIfAbsent(clientId, ClientSession::new).subscriptions().add(topicFilter);
    }

    @Override
    public boolean removeSubscription(String clientId, String topicFilter) {
        ClientSession session = sessions.get(clientId);
        if (session != null) {
            return session.subscriptions().remove(topicFilter);
        }
        return false;
    }

    @Override
    public Optional<ClientSession> find(String clientId) {
        return Optional.ofNullable(sessions.get(clientId));
    }
}
