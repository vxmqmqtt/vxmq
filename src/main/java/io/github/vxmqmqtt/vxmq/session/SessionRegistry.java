package io.github.vxmqmqtt.vxmq.session;

import io.netty.handler.codec.mqtt.MqttQoS;

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
    void addSubscription(String clientId, String topicFilter, MqttQoS grantedQos);

    /**
     * Removes a topic filter from the client's session state.
     */
    boolean removeSubscription(String clientId, String topicFilter);

    /**
     * Queues one offline QoS 1 message for a persistent session.
     */
    void enqueueOfflineMessage(String clientId, QueuedMessage queuedMessage);

    /**
     * Converts all queued offline messages into inflight deliveries after reconnect.
     */
    List<InflightMessage> drainQueuedMessages(String clientId);

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
     * Marks one inflight QoS 1 delivery as acknowledged.
     */
    boolean acknowledge(String clientId, int packetId);

    /**
     * Returns the session for a client if it exists.
     */
    Optional<ClientSession> find(String clientId);
}
