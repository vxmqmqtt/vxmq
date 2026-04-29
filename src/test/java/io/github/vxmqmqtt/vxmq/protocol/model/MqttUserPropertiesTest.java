package io.github.vxmqmqtt.vxmq.protocol.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // Verifies that packet-level property containers normalize nulls to empty properties.
    @Test
    void shouldNormalizeNullPacketProperties() {
        assertTrue(new ConnectProperties(null).isEmpty());
        assertTrue(new SubscriptionProperties(null).isEmpty());
        assertTrue(new UnsubscribeProperties(null).isEmpty());
        assertTrue(new PublishProperties(null).isEmpty());
    }
}
