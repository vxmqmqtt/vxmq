package io.github.vxmqmqtt.vxmq.session;

import io.github.vxmqmqtt.vxmq.protocol.model.WillMessage;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishProperties;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.time.Instant;
import java.util.List;
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
    default void addSubscription(String clientId, String topicFilter, MqttQoS grantedQos) {
        addSubscription(new SubscriptionBinding(clientId, topicFilter, grantedQos));
    }

    /**
     * Adds a topic filter with MQTT 5 subscription metadata to the client's session state.
     */
    void addSubscription(SubscriptionBinding subscriptionBinding);

    /**
     * Removes a topic filter from the client's session state.
     */
    boolean removeSubscription(String clientId, String topicFilter);

    /**
     * Queues one offline QoS 1 message for a persistent session.
     */
    void enqueueOfflineMessage(String clientId, QueuedMessage queuedMessage);

    /**
     * Queues one online outbound QoS 1 / QoS 2 message until the subscriber receive window opens.
     */
    void enqueuePendingOutboundMessage(String clientId, QueuedMessage queuedMessage);

    /**
     * Converts all queued offline messages into inflight deliveries after reconnect.
     */
    List<InflightMessage> drainQueuedMessages(String clientId);

    /**
     * Converts all non-expired queued offline messages into inflight deliveries after reconnect.
     */
    default List<InflightMessage> drainQueuedMessages(String clientId, Instant now) {
        return drainQueuedMessages(clientId);
    }

    /**
     * Converts pending online outbound messages into inflight deliveries when receive window is available.
     */
    List<InflightMessage> drainPendingOutboundMessages(String clientId, Instant now);

    /**
     * Creates one inflight delivery for an online QoS 1 publish.
     */
    Optional<InflightMessage> createInflightMessage(
            String clientId,
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            boolean fromOfflineQueue);

    /**
     * Creates one inflight delivery with MQTT 5 subscription identifiers.
     */
    Optional<InflightMessage> createInflightMessage(
            String clientId,
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            boolean fromOfflineQueue,
            List<Integer> subscriptionIdentifiers);

    /**
     * Creates one inflight delivery with MQTT 5 PUBLISH properties and subscription identifiers.
     */
    Optional<InflightMessage> createInflightMessage(
            String clientId,
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            boolean fromOfflineQueue,
            PublishProperties properties,
            List<Integer> subscriptionIdentifiers);

    /**
     * Starts or reuses an inbound QoS 2 publish transaction for the publisher packet id.
     */
    Optional<InboundQos2Message> startInboundQos2Message(
            String clientId,
            int packetId,
            String topicName,
            byte[] payload,
            boolean retain,
            boolean duplicate);

    /**
     * Starts or reuses an inbound QoS 2 publish transaction with MQTT 5 PUBLISH properties.
     */
    Optional<InboundQos2Message> startInboundQos2Message(
            String clientId,
            int packetId,
            String topicName,
            byte[] payload,
            boolean retain,
            boolean duplicate,
            PublishProperties properties);

    /**
     * Returns whether an inbound QoS 2 transaction with the supplied packet id already exists.
     */
    boolean hasInboundQos2Message(String clientId, int packetId);

    /**
     * Completes and removes an inbound QoS 2 transaction after PUBREL.
     */
    Optional<InboundQos2Message> completeInboundQos2Message(String clientId, int packetId);

    /**
     * Marks an outbound QoS 2 delivery as having received PUBREC and requiring PUBREL.
     */
    Optional<InflightMessage> markOutboundQos2PubRec(String clientId, int packetId);

    /**
     * Clears an outbound QoS 2 delivery after PUBCOMP.
     */
    boolean completeOutboundQos2(String clientId, int packetId);

    /**
     * Returns unfinished outbound QoS 2 deliveries for reconnect replay.
     */
    List<InflightMessage> outboundQos2InflightMessages(String clientId);

    /**
     * Marks one inflight QoS 1 delivery as acknowledged.
     */
    boolean acknowledge(String clientId, int packetId);

    /**
     * Clears and returns the current will message for the live session if the connection still matches.
     */
    Optional<WillMessage> takeWillMessage(String clientId, String connectionId);

    /**
     * Discards the current will message for the live session if the connection still matches.
     */
    void discardWillMessage(String clientId, String connectionId);

    /**
     * Returns the session for a client if it exists.
     */
    Optional<ClientSession> find(String clientId);
}
