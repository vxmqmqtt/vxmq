package io.github.vxmqmqtt.vxmq.protocol;

import io.github.vxmqmqtt.vxmq.observability.BrokerDiagnosticEvent;
import io.github.vxmqmqtt.vxmq.observability.BrokerDiagnosticSeverity;
import io.github.vxmqmqtt.vxmq.observability.BrokerEventSink;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;

/**
 * Centralizes protocol diagnostic event construction so protocol collaborators
 * share the same event names, severity defaults, and MQTT metadata fields.
 */
final class ProtocolDiagnostics {

    private final BrokerEventSink brokerEventSink;

    ProtocolDiagnostics(BrokerEventSink brokerEventSink) {
        this.brokerEventSink = brokerEventSink;
    }

    DiagnosticBuilder diagnostic(
            ClientConnection connection,
            String event,
            String operation,
            Object reason) {
        return new DiagnosticBuilder(BrokerDiagnosticEvent.builder(event)
                .severity(BrokerDiagnosticSeverity.WARN)
                .operation(operation)
                .reason(reason)
                .connection(connection));
    }

    void publishRejected(
            ClientConnection connection,
            PublishRequest request,
            Object reason,
            Object mqttReasonCode) {
        diagnostic(connection, "publish_rejected", "PUBLISH", reason)
                .mqttReasonCode(mqttReasonCode)
                .topic(request.topicName())
                .packetId(request.packetId())
                .qos(request.qos())
                .buildDiagnostic();
    }

    final class DiagnosticBuilder {

        private final BrokerDiagnosticEvent.Builder delegate;

        private DiagnosticBuilder(BrokerDiagnosticEvent.Builder delegate) {
            this.delegate = delegate;
        }

        DiagnosticBuilder severity(BrokerDiagnosticSeverity severity) {
            delegate.severity(severity);
            return this;
        }

        DiagnosticBuilder mqttReasonCode(Object mqttReasonCode) {
            delegate.mqttReasonCode(mqttReasonCode);
            return this;
        }

        DiagnosticBuilder mqttReturnCode(Object mqttReturnCode) {
            delegate.mqttReturnCode(mqttReturnCode);
            return this;
        }

        DiagnosticBuilder topic(String topic) {
            delegate.topic(topic);
            return this;
        }

        DiagnosticBuilder topicFilter(String topicFilter) {
            delegate.topicFilter(topicFilter);
            return this;
        }

        DiagnosticBuilder packetId(int packetId) {
            delegate.packetId(packetId);
            return this;
        }

        DiagnosticBuilder qos(int qos) {
            delegate.qos(qos);
            return this;
        }

        void buildDiagnostic() {
            brokerEventSink.diagnostic(delegate.build());
        }
    }
}
