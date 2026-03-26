package io.github.vxmqmqtt.vxmq.session;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session store used by the early broker milestones.
 */
@ApplicationScoped
public class InMemorySessionRegistry implements SessionRegistry {

    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();

    @Override
    public SessionOpenResult openSession(String clientId, SessionOpenRequest request) {
        ClientSession clearedSession = removeExpiredSessionIfAny(clientId);
        ClientSession existingSession = sessions.get(clientId);
        if (request.startFreshSession()) {
            ClientSession removedSession = sessions.remove(clientId);
            if (removedSession != null) {
                clearedSession = removedSession;
            }
            ClientSession newSession = new ClientSession(clientId);
            newSession.activate(request.connectionId(), request.persistent(), request.sessionExpiryIntervalSeconds());
            sessions.put(clientId, newSession);
            return new SessionOpenResult(newSession, false, clearedSession);
        }

        if (existingSession != null) {
            existingSession.activate(request.connectionId(), request.persistent(), request.sessionExpiryIntervalSeconds());
            return new SessionOpenResult(existingSession, true, clearedSession);
        }

        ClientSession newSession = new ClientSession(clientId);
        newSession.activate(request.connectionId(), request.persistent(), request.sessionExpiryIntervalSeconds());
        sessions.put(clientId, newSession);
        return new SessionOpenResult(newSession, false, clearedSession);
    }

    @Override
    public Optional<ClientSession> onConnectionClosed(String clientId, String connectionId) {
        ClientSession session = find(clientId).orElse(null);
        // Avoid disconnecting a newer connection that already took over the same client id.
        if (session == null || !Objects.equals(connectionId, session.connectionId())) {
            return Optional.empty();
        }

        if (!session.persistent()) {
            sessions.remove(clientId, session);
            return Optional.of(session);
        }

        Long sessionExpiryIntervalSeconds = session.sessionExpiryIntervalSeconds();
        if (sessionExpiryIntervalSeconds == null) {
            session.markOffline(null);
            return Optional.empty();
        }

        if (sessionExpiryIntervalSeconds <= 0) {
            sessions.remove(clientId, session);
            return Optional.of(session);
        }

        session.markOffline(Instant.now().plusSeconds(sessionExpiryIntervalSeconds));
        return Optional.empty();
    }

    @Override
    public Optional<ClientSession> removeSession(String clientId) {
        return Optional.ofNullable(sessions.remove(clientId));
    }

    @Override
    public void addSubscription(String clientId, String topicFilter) {
        sessionForMutation(clientId).subscriptions().add(topicFilter);
    }

    @Override
    public boolean removeSubscription(String clientId, String topicFilter) {
        ClientSession session = sessionForMutation(clientId);
        if (session != null) {
            return session.subscriptions().remove(topicFilter);
        }
        return false;
    }

    @Override
    public Optional<ClientSession> find(String clientId) {
        removeExpiredSessionIfAny(clientId);
        return Optional.ofNullable(sessions.get(clientId));
    }

    private ClientSession sessionForMutation(String clientId) {
        removeExpiredSessionIfAny(clientId);
        return sessions.computeIfAbsent(clientId, ClientSession::new);
    }

    private ClientSession removeExpiredSessionIfAny(String clientId) {
        ClientSession session = sessions.get(clientId);
        if (session == null || session.connectionId() != null || session.expiresAt() == null) {
            return null;
        }

        if (!session.expiresAt().isAfter(Instant.now())) {
            sessions.remove(clientId, session);
            return session;
        }
        return null;
    }
}
