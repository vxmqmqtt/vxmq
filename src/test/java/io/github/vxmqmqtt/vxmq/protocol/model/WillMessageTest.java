package io.github.vxmqmqtt.vxmq.protocol.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for immutable will message modeling.
 */
class WillMessageTest {

    // Verifies that payload access returns a defensive copy so session state cannot be mutated from the outside.
    @Test
    void shouldExposeDefensivePayloadCopy() {
        WillMessage willMessage = new WillMessage(
                "clients/disconnected",
                "offline".getBytes(),
                MqttQoS.AT_LEAST_ONCE,
                true);

        byte[] payloadCopy = willMessage.payloadCopy();

        assertArrayEquals("offline".getBytes(), payloadCopy);
        assertNotSame(willMessage.payload(), payloadCopy);
        payloadCopy[0] = 'X';
        assertArrayEquals("offline".getBytes(), willMessage.payloadCopy());
    }

    // Verifies that will metadata is preserved exactly as modeled.
    @Test
    void shouldPreserveTopicQosAndRetainFlag() {
        WillMessage willMessage = new WillMessage(
                "status/last-will",
                "gone".getBytes(),
                MqttQoS.AT_MOST_ONCE,
                false);

        assertEquals("status/last-will", willMessage.topicName());
        assertEquals(MqttQoS.AT_MOST_ONCE, willMessage.qos());
        assertTrue(!willMessage.retain());
    }

    // Verifies that MQTT 5 will user properties are modeled as publish properties.
    @Test
    void shouldPreserveWillPublishProperties() {
        PublishProperties properties = new PublishProperties(
                new MqttUserProperties(java.util.List.of(new MqttUserProperty("trace", "will"))));
        WillMessage willMessage = new WillMessage(
                "status/last-will",
                "gone".getBytes(),
                MqttQoS.AT_MOST_ONCE,
                false,
                properties);

        assertEquals(properties, willMessage.properties());
    }
}
