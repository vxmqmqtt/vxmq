package io.github.vxmqmqtt.vxmq.observability;

import io.github.vxmqmqtt.vxmq.transport.ClientConnection;

/**
 * Receives broker lifecycle and protocol events for logging or metrics.
 */
public interface BrokerEventSink {

    void transportStarted(String host, int port);

    void transportStopped();

    void connectionAccepted(ClientConnection connection);

    void subscriptionAdded(ClientConnection connection, String topicFilter);

    void subscriptionRemoved(ClientConnection connection, String topicFilter);

    void messageRouted(ClientConnection connection, String topicName, int matchedClients);

    void protocolWarning(ClientConnection connection, String message);
}
