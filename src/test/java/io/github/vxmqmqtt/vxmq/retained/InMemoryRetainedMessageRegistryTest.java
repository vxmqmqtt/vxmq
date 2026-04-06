package io.github.vxmqmqtt.vxmq.retained;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vxmqmqtt.vxmq.routing.DefaultTopicMatcher;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for in-memory retained-message storage and topic-filter matching.
 */
class InMemoryRetainedMessageRegistryTest {

    private InMemoryRetainedMessageRegistry retainedMessageRegistry;

    @BeforeEach
    void setUp() {
        retainedMessageRegistry = new InMemoryRetainedMessageRegistry(new DefaultTopicMatcher());
    }

    // Verifies that storing a retained message makes it queryable by exact topic name.
    @Test
    void shouldStoreRetainedMessage() {
        retainedMessageRegistry.putRetained("sensors/room-1/temperature", "payload".getBytes(), MqttQoS.AT_MOST_ONCE);

        assertTrue(retainedMessageRegistry.findExact("sensors/room-1/temperature").isPresent());
    }

    // Verifies that storing a second retained message on the same topic replaces the previous one.
    @Test
    void shouldReplaceRetainedMessageForSameTopic() {
        retainedMessageRegistry.putRetained("sensors/room-1/temperature", "first".getBytes(), MqttQoS.AT_MOST_ONCE);
        retainedMessageRegistry.putRetained("sensors/room-1/temperature", "second".getBytes(), MqttQoS.AT_LEAST_ONCE);

        RetainedMessage retainedMessage =
                retainedMessageRegistry.findExact("sensors/room-1/temperature").orElseThrow();
        assertEquals(MqttQoS.AT_LEAST_ONCE, retainedMessage.qos());
        assertEquals("second", new String(retainedMessage.payloadCopy()));
    }

    // Verifies that removing a retained message clears the exact topic lookup.
    @Test
    void shouldRemoveRetainedMessage() {
        retainedMessageRegistry.putRetained("sensors/room-1/temperature", "payload".getBytes(), MqttQoS.AT_MOST_ONCE);

        assertTrue(retainedMessageRegistry.removeRetained("sensors/room-1/temperature"));
        assertTrue(retainedMessageRegistry.findExact("sensors/room-1/temperature").isEmpty());
    }

    // Verifies that wildcard topic filters return only the matching retained topics.
    @Test
    void shouldMatchRetainedMessagesByWildcardFilter() {
        retainedMessageRegistry.putRetained("sensors/room-1/temperature", "a".getBytes(), MqttQoS.AT_MOST_ONCE);
        retainedMessageRegistry.putRetained("sensors/room-2/temperature", "b".getBytes(), MqttQoS.AT_MOST_ONCE);
        retainedMessageRegistry.putRetained("alerts/room-1/temperature", "c".getBytes(), MqttQoS.AT_MOST_ONCE);

        assertEquals(2, retainedMessageRegistry.findMatching("sensors/+/temperature").size());
        assertEquals(3, retainedMessageRegistry.findMatching("#").size());
        assertFalse(retainedMessageRegistry.findMatching("alerts/+/humidity").iterator().hasNext());
    }
}
