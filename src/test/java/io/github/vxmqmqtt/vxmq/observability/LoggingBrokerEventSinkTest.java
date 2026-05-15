package io.github.vxmqmqtt.vxmq.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.vxmqmqtt.vxmq.session.InMemorySessionRegistry;
import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class LoggingBrokerEventSinkTest {

    @Test
    void shouldCountOnlyWarningAndErrorDiagnosticsAsProtocolWarnings() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        LoggingBrokerEventSink sink = new LoggingBrokerEventSink(new BrokerMetrics(
                meterRegistry,
                new BrokerRuntimeState(),
                new ClientConnectionRegistry(),
                new InMemorySessionRegistry()));

        sink.diagnostic(BrokerDiagnosticEvent.builder("connection_closed")
                .severity(BrokerDiagnosticSeverity.INFO)
                .operation("CLOSE")
                .reason("CLIENT_DISCONNECT")
                .build());
        sink.diagnostic(BrokerDiagnosticEvent.builder("publish_rejected")
                .severity(BrokerDiagnosticSeverity.WARN)
                .operation("PUBLISH")
                .reason("TOPIC_NAME_INVALID")
                .build());
        sink.diagnostic(BrokerDiagnosticEvent.builder("delivery_failed")
                .severity(BrokerDiagnosticSeverity.ERROR)
                .operation("PUBLISH")
                .reason("TRANSPORT_WRITE_FAILED")
                .build());

        assertEquals(2.0, meterRegistry.get("vxmq_protocol_warnings").counter().count());
    }
}
