package io.github.vxmqmqtt.vxmq.session;

import io.github.vxmqmqtt.vxmq.config.BrokerRuntimeConfig;
import io.github.vxmqmqtt.vxmq.protocol.model.WillMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session store used by the early broker milestones.
 */
@ApplicationScoped
public class InMemorySessionRegistry implements SessionRegistry {

    static final int DEFAULT_OFFLINE_QUEUE_CAPACITY = 1_024;

    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();
    private volatile int offlineQueueCapacity = DEFAULT_OFFLINE_QUEUE_CAPACITY;

    public InMemorySessionRegistry() {
    }

    public InMemorySessionRegistry(int offlineQueueCapacity) {
        this.offlineQueueCapacity = offlineQueueCapacity;
    }

    /**
     * Applies runtime configuration when the registry is managed by CDI, while keeping plain tests lightweight.
     */
    @Inject
    void configure(BrokerRuntimeConfig brokerRuntimeConfig) {
        this.offlineQueueCapacity = brokerRuntimeConfig.offlineQueueCapacityPerSession();
    }

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
            newSession.activate(
                    request.connectionId(),
                    request.persistent(),
                    request.sessionExpiryIntervalSeconds(),
                    request.willMessage());
            sessions.put(clientId, newSession);
            return new SessionOpenResult(newSession, false, clearedSession);
        }

        if (existingSession != null) {
            existingSession.activate(
                    request.connectionId(),
                    request.persistent(),
                    request.sessionExpiryIntervalSeconds(),
                    request.willMessage());
            return new SessionOpenResult(existingSession, true, clearedSession);
        }

        ClientSession newSession = new ClientSession(clientId);
        newSession.activate(
                request.connectionId(),
                request.persistent(),
                request.sessionExpiryIntervalSeconds(),
                request.willMessage());
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
            session.requeueInflightMessages(offlineQueueCapacity);
            session.markOffline(null);
            return Optional.empty();
        }

        if (sessionExpiryIntervalSeconds <= 0) {
            sessions.remove(clientId, session);
            return Optional.of(session);
        }

        session.requeueInflightMessages(offlineQueueCapacity);
        session.markOffline(Instant.now().plusSeconds(sessionExpiryIntervalSeconds));
        return Optional.empty();
    }

    @Override
    public Optional<ClientSession> removeSession(String clientId) {
        return Optional.ofNullable(sessions.remove(clientId));
    }

    @Override
    public void addSubscription(String clientId, String topicFilter, MqttQoS grantedQos) {
        sessionForMutation(clientId).putSubscription(topicFilter, grantedQos);
    }

    @Override
    public boolean removeSubscription(String clientId, String topicFilter) {
        ClientSession session = sessionForMutation(clientId);
        if (session != null) {
            return session.removeSubscription(topicFilter);
        }
        return false;
    }

    @Override
    public void enqueueOfflineMessage(String clientId, QueuedMessage queuedMessage) {
        sessionForMutation(clientId).enqueueOfflineMessage(queuedMessage, offlineQueueCapacity);
    }

    @Override
    public List<InflightMessage> drainQueuedMessages(String clientId) {
        return find(clientId)
                .map(ClientSession::drainQueuedMessagesToInflight)
                .orElseGet(List::of);
    }

    @Override
    public Optional<InflightMessage> createInflightMessage(
            String clientId,
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            boolean fromOfflineQueue) {
        return find(clientId)
                .map(session -> session.createInflightMessage(topicName, payload, qos, retain, duplicate, fromOfflineQueue));
    }

    @Override
    public boolean acknowledge(String clientId, int packetId) {
        return find(clientId)
                .map(session -> session.acknowledge(packetId))
                .orElse(false);
    }

    @Override
    public Optional<WillMessage> takeWillMessage(String clientId, String connectionId) {
        ClientSession session = find(clientId).orElse(null);
        if (session == null || !Objects.equals(connectionId, session.connectionId())) {
            return Optional.empty();
        }
        return Optional.ofNullable(session.takeWillMessage());
    }

    @Override
    public void discardWillMessage(String clientId, String connectionId) {
        ClientSession session = find(clientId).orElse(null);
        if (session == null || !Objects.equals(connectionId, session.connectionId())) {
            return;
        }
        session.clearWillMessage();
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
