package io.github.vxmqmqtt.vxmq.session;

import io.netty.handler.codec.mqtt.MqttQoS;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final Map<String, MqttQoS> subscriptions = new ConcurrentHashMap<>();
    private final Deque<QueuedMessage> queuedMessages = new ArrayDeque<>();
    private final Map<Integer, InflightMessage> inflightMessages = new LinkedHashMap<>();
    private int nextPacketId = 1;

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
     * Returns the current subscription filters stored on the session.
     */
    public Set<String> subscriptions() {
        return subscriptions.keySet();
    }

    /**
     * Stores or replaces one subscription entry on the session.
     */
    public void putSubscription(String topicFilter, MqttQoS grantedQos) {
        subscriptions.put(topicFilter, grantedQos);
    }

    /**
     * Removes one subscription entry from the session.
     */
    public boolean removeSubscription(String topicFilter) {
        return subscriptions.remove(topicFilter) != null;
    }

    /**
     * Returns the granted qos for a stored subscription, if present.
     */
    public MqttQoS subscriptionQos(String topicFilter) {
        return subscriptions.get(topicFilter);
    }

    /**
     * Returns the number of queued offline messages.
     */
    public synchronized int queuedMessageCount() {
        return queuedMessages.size();
    }

    /**
     * Returns the number of QoS 1 messages waiting for PUBACK.
     */
    public synchronized int inflightMessageCount() {
        return inflightMessages.size();
    }

    /**
     * Adds one offline message to the queue, dropping the oldest entry when the capacity is exceeded.
     */
    public synchronized void enqueueOfflineMessage(QueuedMessage message, int capacity) {
        while (queuedMessages.size() >= capacity) {
            queuedMessages.pollFirst();
        }
        queuedMessages.addLast(new QueuedMessage(
                message.topicName(),
                message.payloadCopy(),
                message.qos(),
                message.retain(),
                message.duplicate()));
    }

    /**
     * Creates one inflight record for an immediately delivered QoS 1 message.
     */
    public synchronized InflightMessage createInflightMessage(
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            boolean fromOfflineQueue) {
        int packetId = allocatePacketId();
        InflightMessage inflightMessage = new InflightMessage(
                packetId,
                topicName,
                payload == null ? null : payload.clone(),
                qos,
                retain,
                duplicate,
                fromOfflineQueue);
        inflightMessages.put(packetId, inflightMessage);
        return inflightMessage;
    }

    /**
     * Drains queued offline messages into inflight deliveries in FIFO order.
     */
    public synchronized List<InflightMessage> drainQueuedMessagesToInflight() {
        List<InflightMessage> drained = new ArrayList<>();
        while (!queuedMessages.isEmpty()) {
            QueuedMessage queuedMessage = queuedMessages.pollFirst();
            drained.add(createInflightMessage(
                    queuedMessage.topicName(),
                    queuedMessage.payloadCopy(),
                    queuedMessage.qos(),
                    queuedMessage.retain(),
                    queuedMessage.duplicate(),
                    true));
        }
        return drained;
    }

    /**
     * Marks one QoS 1 packet as acknowledged by the subscriber.
     */
    public synchronized boolean acknowledge(int packetId) {
        return inflightMessages.remove(packetId) != null;
    }

    /**
     * Moves all unacknowledged inflight messages back to the front of the offline queue.
     */
    public synchronized void requeueInflightMessages(int capacity) {
        Collection<InflightMessage> snapshot = new ArrayList<>(inflightMessages.values());
        inflightMessages.clear();
        List<QueuedMessage> queued = snapshot.stream()
                .map(InflightMessage::toQueuedMessage)
                .toList();
        for (int index = queued.size() - 1; index >= 0; index--) {
            while (queuedMessages.size() >= capacity) {
                queuedMessages.pollFirst();
            }
            queuedMessages.addFirst(queued.get(index));
        }
    }

    /**
     * Returns a copy of the current inflight deliveries for assertions.
     */
    public synchronized List<InflightMessage> inflightMessages() {
        return new ArrayList<>(inflightMessages.values());
    }

    /**
     * Returns a copy of the current offline queue for assertions.
     */
    public synchronized List<QueuedMessage> queuedMessages() {
        return new ArrayList<>(queuedMessages);
    }

    private int allocatePacketId() {
        for (int attempts = 0; attempts < 65_535; attempts++) {
            int candidate = nextPacketId;
            nextPacketId++;
            if (nextPacketId > 65_535) {
                nextPacketId = 1;
            }
            if (candidate == 0) {
                continue;
            }
            if (!inflightMessages.containsKey(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No MQTT packet identifiers available for session " + clientId);
    }
}
