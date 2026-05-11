package io.github.vxmqmqtt.vxmq.session;

import io.github.vxmqmqtt.vxmq.config.BrokerRuntimeConfig;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.WillMessage;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
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
    private final int offlineQueueCapacity;

    public InMemorySessionRegistry() {
        this(DEFAULT_OFFLINE_QUEUE_CAPACITY);
    }

    public InMemorySessionRegistry(int offlineQueueCapacity) {
        this.offlineQueueCapacity = offlineQueueCapacity;
    }

    /**
     * Applies runtime configuration when the registry is managed by CDI, while keeping plain tests lightweight.
     */
    @Inject
    public InMemorySessionRegistry(BrokerRuntimeConfig brokerRuntimeConfig) {
        this(brokerRuntimeConfig.offlineQueueCapacityPerSession());
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
                    request.willMessage(),
                    request.receiveMaximum());
            sessions.put(clientId, newSession);
            return new SessionOpenResult(newSession, false, clearedSession);
        }

        if (existingSession != null) {
            existingSession.activate(
                    request.connectionId(),
                    request.persistent(),
                    request.sessionExpiryIntervalSeconds(),
                    request.willMessage(),
                    request.receiveMaximum());
            return new SessionOpenResult(existingSession, true, clearedSession);
        }

        ClientSession newSession = new ClientSession(clientId);
        newSession.activate(
                request.connectionId(),
                request.persistent(),
                request.sessionExpiryIntervalSeconds(),
                request.willMessage(),
                request.receiveMaximum());
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
    public void addSubscription(SubscriptionBinding subscriptionBinding) {
        sessionForMutation(subscriptionBinding.clientId()).putSubscription(subscriptionBinding);
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
    public void enqueuePendingOutboundMessage(String clientId, QueuedMessage queuedMessage) {
        sessionForMutation(clientId).enqueuePendingOutboundMessage(queuedMessage, offlineQueueCapacity);
    }

    @Override
    public List<InflightMessage> drainQueuedMessages(String clientId) {
        return find(clientId)
                .map(ClientSession::drainQueuedMessagesToInflight)
                .orElseGet(List::of);
    }

    @Override
    public List<InflightMessage> drainQueuedMessages(String clientId, Instant now) {
        return find(clientId)
                .map(session -> session.drainQueuedMessagesToInflight(now))
                .orElseGet(List::of);
    }

    @Override
    public List<InflightMessage> drainPendingOutboundMessages(String clientId, Instant now) {
        return find(clientId)
                .map(session -> session.drainPendingOutboundMessagesToInflight(now))
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
        return createInflightMessage(
                clientId,
                topicName,
                payload,
                qos,
                retain,
                duplicate,
                fromOfflineQueue,
                PublishProperties.empty(),
                List.of());
    }

    @Override
    public Optional<InflightMessage> createInflightMessage(
            String clientId,
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            boolean fromOfflineQueue,
            List<Integer> subscriptionIdentifiers) {
        return createInflightMessage(
                clientId,
                topicName,
                payload,
                qos,
                retain,
                duplicate,
                fromOfflineQueue,
                PublishProperties.empty(),
                subscriptionIdentifiers);
    }

    @Override
    public Optional<InflightMessage> createInflightMessage(
            String clientId,
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            boolean fromOfflineQueue,
            PublishProperties properties,
            List<Integer> subscriptionIdentifiers) {
        return find(clientId)
                .map(session -> session.createInflightMessage(
                        topicName,
                        payload,
                        qos,
                        retain,
                        duplicate,
                        fromOfflineQueue,
                        properties,
                        subscriptionIdentifiers));
    }

    @Override
    public Optional<InboundQos2Message> startInboundQos2Message(
            String clientId,
            int packetId,
            String topicName,
            byte[] payload,
            boolean retain,
            boolean duplicate) {
        return startInboundQos2Message(
                clientId,
                packetId,
                topicName,
                payload,
                retain,
                duplicate,
                PublishProperties.empty());
    }

    @Override
    public Optional<InboundQos2Message> startInboundQos2Message(
            String clientId,
            int packetId,
            String topicName,
            byte[] payload,
            boolean retain,
            boolean duplicate,
            PublishProperties properties) {
        return find(clientId)
                .map(session -> session.startInboundQos2Message(packetId, topicName, payload, retain, duplicate, properties));
    }

    @Override
    public boolean hasInboundQos2Message(String clientId, int packetId) {
        return find(clientId)
                .map(session -> session.hasInboundQos2Message(packetId))
                .orElse(false);
    }

    @Override
    public Optional<InboundQos2Message> completeInboundQos2Message(String clientId, int packetId) {
        return find(clientId)
                .map(session -> session.completeInboundQos2Message(packetId));
    }

    @Override
    public Optional<InflightMessage> markOutboundQos2PubRec(String clientId, int packetId) {
        return find(clientId)
                .map(session -> session.markOutboundQos2PubRec(packetId));
    }

    @Override
    public boolean completeOutboundQos2(String clientId, int packetId) {
        return find(clientId)
                .map(session -> session.completeOutboundQos2(packetId))
                .orElse(false);
    }

    @Override
    public List<InflightMessage> outboundQos2InflightMessages(String clientId) {
        return find(clientId)
                .map(ClientSession::outboundQos2InflightMessages)
                .orElseGet(List::of);
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
