package io.github.vxmqmqtt.vxmq.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BrokerDiagnosticEventTest {

    @Test
    void shouldFormatStableKeyValueFields() {
        ClientConnection connection = new ClientConnection(
                "connection-1",
                "127.0.0.1:1883",
                "client-a",
                "MQTT",
                5,
                true);
        connection.assignClientId("client-a");

        BrokerDiagnosticEvent event = BrokerDiagnosticEvent.builder("publish_rejected")
                .severity(BrokerDiagnosticSeverity.WARN)
                .operation("PUBLISH")
                .reason("TOPIC_NAME_INVALID")
                .connection(connection)
                .mqttReasonCode("TOPIC_NAME_INVALID")
                .topic("sensors/#")
                .packetId(7)
                .qos(1)
                .transportAction("mqtt5_disconnect")
                .build();

        assertEquals(
                "event=publish_rejected severity=WARN operation=PUBLISH reason=TOPIC_NAME_INVALID "
                        + "connectionId=connection-1 clientId=client-a requestedClientId=client-a "
                        + "remote=127.0.0.1:1883 protocolVersion=5 mqttReasonCode=TOPIC_NAME_INVALID "
                        + "topic=sensors/# packetId=7 qos=1 transportAction=mqtt5_disconnect",
                event.format());
    }

    @Test
    void shouldOmitMissingFields() {
        BrokerDiagnosticEvent event = BrokerDiagnosticEvent.builder("connection_closed")
                .severity(BrokerDiagnosticSeverity.INFO)
                .operation("CLOSE")
                .reason("SOCKET_CLOSED")
                .build();

        assertEquals("event=connection_closed severity=INFO operation=CLOSE reason=SOCKET_CLOSED", event.format());
    }

    @Test
    void shouldNotModelSensitiveFields() {
        String fields = Arrays.stream(BrokerDiagnosticEvent.class.getDeclaredFields())
                .map(Field::getName)
                .map(String::toLowerCase)
                .reduce("", (left, right) -> left + " " + right);

        assertFalse(fields.contains("password"));
        assertFalse(fields.contains("payload"));
        assertFalse(fields.contains("correlation"));
        assertFalse(fields.contains("userpropert"));
    }
}
