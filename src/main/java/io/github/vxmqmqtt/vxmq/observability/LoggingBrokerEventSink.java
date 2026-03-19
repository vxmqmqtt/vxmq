package io.github.vxmqmqtt.vxmq.observability;

import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LoggingBrokerEventSink implements BrokerEventSink {

    private static final Logger LOG = Logger.getLogger(LoggingBrokerEventSink.class);

    @Override
    public void transportStarted(String host, int port) {
        LOG.infov("MQTT transport started on {0}:{1,number,#}", host, port);
    }

    @Override
    public void transportStopped() {
        LOG.info("MQTT transport stopped");
    }

    @Override
    public void connectionAccepted(ClientConnection connection) {
        LOG.infov("Accepted MQTT connection id={0}, clientId={1}, remote={2}",
                connection.internalId(),
                connection.effectiveClientId(),
                connection.remoteAddress());
    }

    @Override
    public void subscriptionAdded(ClientConnection connection, String topicFilter) {
        LOG.infov("Registered subscription clientId={0}, filter={1}",
                connection.effectiveClientId(),
                topicFilter);
    }

    @Override
    public void subscriptionRemoved(ClientConnection connection, String topicFilter) {
        LOG.infov("Removed subscription clientId={0}, filter={1}",
                connection.effectiveClientId(),
                topicFilter);
    }

    @Override
    public void messageRouted(ClientConnection connection, String topicName, int matchedClients) {
        LOG.infov("Routed publish from clientId={0}, topic={1}, matchedClients={2}",
                connection.effectiveClientId(),
                topicName,
                matchedClients);
    }

    @Override
    public void protocolWarning(ClientConnection connection, String message) {
        if (connection == null) {
            LOG.warn(message);
            return;
        }
        LOG.warnv("Protocol warning id={0}, clientId={1}: {2}",
                connection.internalId(),
                connection.effectiveClientId(),
                message);
    }
}
