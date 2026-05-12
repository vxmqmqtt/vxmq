package io.github.vxmqmqtt.vxmq.protocol.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for generic MQTT 5 User Property modeling.
 */
class MqttUserPropertiesTest {

    // Verifies that user properties preserve order and duplicate keys exactly as received.
    @Test
    void shouldPreserveOrderAndDuplicateKeys() {
        MqttUserProperties properties = new MqttUserProperties(List.of(
                new MqttUserProperty("trace", "a"),
                new MqttUserProperty("trace", "b")));

        assertEquals(
                List.of(new MqttUserProperty("trace", "a"), new MqttUserProperty("trace", "b")),
                properties.values());
    }

    // Verifies that the model snapshots input and exposes an immutable list.
    @Test
    void shouldExposeImmutableSnapshot() {
        List<MqttUserProperty> input = new ArrayList<>();
        input.add(new MqttUserProperty("trace", "a"));

        MqttUserProperties properties = new MqttUserProperties(input);
        input.add(new MqttUserProperty("trace", "b"));

        assertEquals(List.of(new MqttUserProperty("trace", "a")), properties.values());
        assertThrows(UnsupportedOperationException.class,
                () -> properties.values().add(new MqttUserProperty("trace", "c")));
    }

    // Verifies that empty properties are represented by the shared empty value.
    @Test
    void shouldExposeEmptyProperties() {
        assertTrue(MqttUserProperties.empty().isEmpty());
        assertEquals(List.of(), MqttUserProperties.empty().values());
    }

    // Verifies that packet-level property containers expose generic MQTT user properties.
    @Test
    void shouldExposePacketPropertyContainers() {
        MqttUserProperties userProperties = new MqttUserProperties(List.of(
                new MqttUserProperty("trace", "a")));

        assertEquals(userProperties, new ConnectProperties(userProperties).userProperties());
        assertEquals(userProperties, new SubscriptionProperties(userProperties).userProperties());
        assertEquals(userProperties, new UnsubscribeProperties(userProperties).userProperties());
        assertEquals(userProperties, new PublishProperties(userProperties).userProperties());
    }

    // Verifies that publish properties can carry a broker-side message expiry deadline.
    @Test
    void shouldExposePublishMessageExpiry() {
        Instant receivedAt = Instant.parse("2026-04-30T00:00:00Z");
        MessageExpiry expiry = MessageExpiry.fromIntervalSeconds(60L, receivedAt);

        PublishProperties properties = new PublishProperties(MqttUserProperties.empty(), expiry);

        assertEquals(expiry, properties.messageExpiry());
        assertFalse(properties.isEmpty());
        assertFalse(expiry.isExpired(receivedAt.plusSeconds(59)));
        assertTrue(expiry.isExpired(receivedAt.plusSeconds(60)));
        assertEquals(1L, expiry.remainingIntervalSeconds(receivedAt.plusSeconds(59)).orElseThrow());
    }

    // Verifies that publish properties expose MQTT 5 request-response properties.
    @Test
    void shouldExposePublishRequestResponseProperties() {
        PublishProperties properties = new PublishProperties(
                MqttUserProperties.empty(),
                MessageExpiry.none(),
                "responses/client-a",
                new byte[]{1, 2, 3});

        assertEquals("responses/client-a", properties.responseTopic());
        assertArrayEquals(new byte[]{1, 2, 3}, properties.correlationData());
        assertFalse(properties.isEmpty());
    }

    // Verifies that Correlation Data cannot be mutated through caller-owned arrays or accessors.
    @Test
    void shouldDefensivelyCopyCorrelationData() {
        byte[] correlationData = new byte[]{1, 2, 3};
        PublishProperties properties = new PublishProperties(
                MqttUserProperties.empty(),
                MessageExpiry.none(),
                "responses/client-a",
                correlationData);
        correlationData[0] = 9;

        byte[] exposed = properties.correlationData();
        exposed[1] = 8;

        assertArrayEquals(new byte[]{1, 2, 3}, properties.correlationData());
    }

    // Verifies that packet-level property containers normalize nulls to empty properties.
    @Test
    void shouldNormalizeNullPacketProperties() {
        assertTrue(new ConnectProperties(null).isEmpty());
        assertNull(ConnectProperties.empty().receiveMaximum());
        assertNull(ConnectProperties.empty().maximumPacketSize());
        assertTrue(new SubscriptionProperties(null).isEmpty());
        assertTrue(new UnsubscribeProperties(null).isEmpty());
        assertTrue(new PublishProperties(null).isEmpty());
        assertTrue(new PublishProperties(null, null).isEmpty());
        assertTrue(new PublishProperties(null, null, null, null).isEmpty());
        assertTrue(MessageExpiry.none().isEmpty());
    }

    // Verifies that explicitly provided CONNECT limits are distinct from absent properties.
    @Test
    void shouldTreatExplicitConnectLimitsAsPresentProperties() {
        ConnectProperties properties = new ConnectProperties(
                MqttUserProperties.empty(),
                ConnectProperties.DEFAULT_RECEIVE_MAXIMUM,
                ConnectProperties.DEFAULT_MAXIMUM_PACKET_SIZE);

        assertFalse(properties.isEmpty());
    }
}
