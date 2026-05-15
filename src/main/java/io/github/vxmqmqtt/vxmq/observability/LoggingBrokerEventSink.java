package io.github.vxmqmqtt.vxmq.observability;

import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Default event sink that exposes broker activity through application logs.
 */
@ApplicationScoped
public class LoggingBrokerEventSink implements BrokerEventSink {

    private static final Logger LOG = Logger.getLogger(LoggingBrokerEventSink.class);
    private final BrokerMetrics brokerMetrics;

    @Inject
    public LoggingBrokerEventSink(BrokerMetrics brokerMetrics) {
        this.brokerMetrics = brokerMetrics;
    }

    @Override
    public void transportStarted(String host, int port) {
        brokerMetrics.transportStarted();
        LOG.infov("MQTT transport started on {0}:{1,number,#}", host, port);
    }

    @Override
    public void transportStopped() {
        brokerMetrics.transportStopped();
        LOG.info("MQTT transport stopped");
    }

    @Override
    public void connectionAccepted(ClientConnection connection) {
        brokerMetrics.connectionAccepted();
        LOG.infov("Accepted MQTT connection id={0}, clientId={1}, remote={2}",
                connection.connectionId(),
                connection.effectiveClientId(),
                connection.remoteAddress());
    }

    @Override
    public void subscriptionAdded(ClientConnection connection, String topicFilter) {
        brokerMetrics.subscriptionAdded();
        LOG.infov("Registered subscription clientId={0}, filter={1}",
                connection.effectiveClientId(),
                topicFilter);
    }

    @Override
    public void subscriptionRemoved(ClientConnection connection, String topicFilter) {
        brokerMetrics.subscriptionRemoved();
        LOG.infov("Removed subscription clientId={0}, filter={1}",
                connection.effectiveClientId(),
                topicFilter);
    }

    @Override
    public void messageRouted(ClientConnection connection, String topicName, int matchedClients) {
        brokerMetrics.messageRouted(matchedClients);
        LOG.infov("Routed publish from clientId={0}, topic={1}, matchedClients={2}",
                connection.effectiveClientId(),
                topicName,
                matchedClients);
    }

    @Override
    public void protocolWarning(ClientConnection connection, String message) {
        brokerMetrics.protocolWarning();
        if (connection == null) {
            LOG.warn(message);
            return;
        }
        LOG.warnv("Protocol warning id={0}, clientId={1}: {2}",
                connection.connectionId(),
                connection.effectiveClientId(),
                message);
    }

    @Override
    public void diagnostic(BrokerDiagnosticEvent event) {
        if (event == null) {
            return;
        }
        if (event.severity() == BrokerDiagnosticSeverity.WARN || event.severity() == BrokerDiagnosticSeverity.ERROR) {
            brokerMetrics.protocolWarning();
        }
        String message = event.format();
        switch (event.severity()) {
            case ERROR -> LOG.error(message);
            case WARN -> LOG.warn(message);
            case INFO -> LOG.info(message);
        }
    }
}
