package io.github.vxmqmqtt.vxmq.session;

import io.github.vxmqmqtt.vxmq.protocol.model.PublishProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.WillMessage;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
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
    // key: topicFilter, value: MqttQoS
    private final Map<String, SubscriptionBinding> subscriptions = new ConcurrentHashMap<>();
    private final Deque<QueuedMessage> queuedMessages = new ArrayDeque<>();
    // key: packetId, value: InflightMessage
    private final Map<Integer, InflightMessage> inflightMessages = new LinkedHashMap<>();
    // key: packetId, value: InboundQos2Message
    private final Map<Integer, InboundQos2Message> inboundQos2Messages = new LinkedHashMap<>();
    private volatile WillMessage willMessage;
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
     * Returns the currently stored will message, or {@code null} if this session has no active will.
     */
    public WillMessage willMessage() {
        return willMessage == null ? null : new WillMessage(
                willMessage.topicName(),
                willMessage.payloadCopy(),
                willMessage.qos(),
                willMessage.retain(),
                willMessage.properties());
    }

    /**
     * Activates the session for a live transport connection and applies the current connect-time policy.
     */
    public void activate(
            String newConnectionId,
            boolean newPersistent,
            Long newSessionExpiryIntervalSeconds,
            WillMessage newWillMessage) {
        this.connectionId = newConnectionId;
        this.persistent = newPersistent;
        this.sessionExpiryIntervalSeconds = newSessionExpiryIntervalSeconds;
        this.expiresAt = null;
        this.willMessage = copyWillMessage(newWillMessage);
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
    public void putSubscription(SubscriptionBinding subscriptionBinding) {
        subscriptions.put(subscriptionBinding.topicFilter(), subscriptionBinding);
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
        SubscriptionBinding subscription = subscriptions.get(topicFilter);
        return subscription == null ? null : subscription.grantedQos();
    }

    /**
     * Returns the stored subscription metadata for one filter, if present.
     */
    public SubscriptionBinding subscription(String topicFilter) {
        return subscriptions.get(topicFilter);
    }

    /**
     * Returns the number of queued offline messages.
     */
    public synchronized int queuedMessageCount() {
        return queuedMessages.size();
    }

    /**
     * Returns the number of QoS 1 / QoS 2 messages waiting for subscriber acknowledgement.
     */
    public synchronized int inflightMessageCount() {
        return inflightMessages.size();
    }

    /**
     * Returns the number of inbound QoS 2 messages waiting for PUBREL.
     */
    public synchronized int inboundQos2MessageCount() {
        return inboundQos2Messages.size();
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
                message.duplicate(),
                message.properties(),
                message.subscriptionIdentifiers()));
    }

    /**
     * Creates one inflight record for an immediately delivered QoS 1 or QoS 2 message.
     */
    public synchronized InflightMessage createInflightMessage(
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            boolean fromOfflineQueue) {
        return createInflightMessage(
                topicName,
                payload,
                qos,
                retain,
                duplicate,
                fromOfflineQueue,
                PublishProperties.empty(),
                List.of());
    }

    public synchronized InflightMessage createInflightMessage(
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            boolean fromOfflineQueue,
            List<Integer> subscriptionIdentifiers) {
        return createInflightMessage(
                topicName,
                payload,
                qos,
                retain,
                duplicate,
                fromOfflineQueue,
                PublishProperties.empty(),
                subscriptionIdentifiers);
    }

    public synchronized InflightMessage createInflightMessage(
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            boolean fromOfflineQueue,
            PublishProperties properties,
            List<Integer> subscriptionIdentifiers) {
        int packetId = allocatePacketId();
        OutboundQos2State qos2State = qos == MqttQoS.EXACTLY_ONCE ? OutboundQos2State.PUBLISH_SENT : null;
        InflightMessage inflightMessage = new InflightMessage(
                packetId,
                topicName,
                payload == null ? null : payload.clone(),
                qos,
                retain,
                duplicate,
                fromOfflineQueue,
                qos2State,
                properties,
                subscriptionIdentifiers);
        inflightMessages.put(packetId, inflightMessage);
        return inflightMessage;
    }

    /**
     * Drains queued offline messages into inflight deliveries in FIFO order.
     */
    public synchronized List<InflightMessage> drainQueuedMessagesToInflight() {
        return drainQueuedMessagesToInflight(null);
    }

    /**
     * Drains non-expired queued offline messages into inflight deliveries in FIFO order.
     */
    public synchronized List<InflightMessage> drainQueuedMessagesToInflight(Instant now) {
        List<InflightMessage> drained = new ArrayList<>();
        while (!queuedMessages.isEmpty()) {
            QueuedMessage queuedMessage = queuedMessages.pollFirst();
            if (now != null && queuedMessage.properties().messageExpiry().isExpired(now)) {
                continue;
            }
            drained.add(createInflightMessage(
                    queuedMessage.topicName(),
                    queuedMessage.payloadCopy(),
                queuedMessage.qos(),
                    queuedMessage.retain(),
                    queuedMessage.duplicate(),
                    true,
                    queuedMessage.properties(),
                    queuedMessage.subscriptionIdentifiers()));
        }
        return drained;
    }

    /**
     * Starts or reuses one inbound QoS 2 transaction for a publisher packet id.
     */
    public synchronized InboundQos2Message startInboundQos2Message(
            int packetId,
            String topicName,
            byte[] payload,
            boolean retain,
            boolean duplicate) {
        return startInboundQos2Message(packetId, topicName, payload, retain, duplicate, PublishProperties.empty());
    }

    public synchronized InboundQos2Message startInboundQos2Message(
            int packetId,
            String topicName,
            byte[] payload,
            boolean retain,
            boolean duplicate,
            PublishProperties properties) {
        return inboundQos2Messages.computeIfAbsent(packetId, ignored -> new InboundQos2Message(
                packetId,
                topicName,
                payload == null ? null : payload.clone(),
                retain,
                duplicate,
                properties));
    }

    /**
     * Completes one inbound QoS 2 transaction after PUBREL.
     */
    public synchronized InboundQos2Message completeInboundQos2Message(int packetId) {
        return inboundQos2Messages.remove(packetId);
    }

    /**
     * Marks one outbound QoS 2 packet as ready for PUBREL.
     */
    public synchronized InflightMessage markOutboundQos2PubRec(int packetId) {
        InflightMessage inflightMessage = inflightMessages.get(packetId);
        if (inflightMessage == null || inflightMessage.qos() != MqttQoS.EXACTLY_ONCE) {
            return null;
        }
        InflightMessage updated = inflightMessage.withQos2State(OutboundQos2State.PUBREL_SENT);
        inflightMessages.put(packetId, updated);
        return updated;
    }

    /**
     * Clears one outbound QoS 2 packet after PUBCOMP.
     */
    public synchronized boolean completeOutboundQos2(int packetId) {
        InflightMessage inflightMessage = inflightMessages.get(packetId);
        if (inflightMessage == null || inflightMessage.qos() != MqttQoS.EXACTLY_ONCE) {
            return false;
        }
        inflightMessages.remove(packetId);
        return true;
    }

    /**
     * Marks outbound QoS 2 messages as duplicates and returns them for reconnect replay.
     */
    public synchronized List<InflightMessage> outboundQos2InflightMessages() {
        List<InflightMessage> qos2Messages = new ArrayList<>();
        for (Map.Entry<Integer, InflightMessage> entry : inflightMessages.entrySet()) {
            InflightMessage inflightMessage = entry.getValue();
            if (inflightMessage.qos() == MqttQoS.EXACTLY_ONCE) {
                InflightMessage duplicate = inflightMessage.withDuplicate(true);
                entry.setValue(duplicate);
                qos2Messages.add(duplicate);
            }
        }
        return qos2Messages;
    }

    /**
     * Marks one QoS 1 packet as acknowledged by the subscriber.
     */
    public synchronized boolean acknowledge(int packetId) {
        InflightMessage inflightMessage = inflightMessages.get(packetId);
        if (inflightMessage == null || inflightMessage.qos() != MqttQoS.AT_LEAST_ONCE) {
            return false;
        }
        inflightMessages.remove(packetId);
        return true;
    }

    /**
     * Moves all unacknowledged QoS 1 inflight messages back to the front of the offline queue.
     * QoS 2 inflight messages stay inflight so their handshake stage can be resumed.
     */
    public synchronized void requeueInflightMessages(int capacity) {
        Collection<InflightMessage> snapshot = inflightMessages.values()
                .stream()
                .filter(inflightMessage -> inflightMessage.qos() == MqttQoS.AT_LEAST_ONCE)
                .toList();
        snapshot.forEach(inflightMessage -> inflightMessages.remove(inflightMessage.packetId()));
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

    /**
     * Clears and returns the current will message so it can be published only once.
     */
    public synchronized WillMessage takeWillMessage() {
        WillMessage current = willMessage;
        willMessage = null;
        return copyWillMessage(current);
    }

    /**
     * Clears the current will message without returning it.
     */
    public synchronized void clearWillMessage() {
        willMessage = null;
    }

    private WillMessage copyWillMessage(WillMessage source) {
        if (source == null) {
            return null;
        }
        return new WillMessage(
                source.topicName(),
                source.payloadCopy(),
                source.qos(),
                source.retain(),
                source.properties());
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
