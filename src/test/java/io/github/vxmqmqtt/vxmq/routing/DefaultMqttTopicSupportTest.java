package io.github.vxmqmqtt.vxmq.routing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifies the minimal topic matching rules used by the in-memory router.
 */
class DefaultMqttTopicSupportTest {

    private final DefaultMqttTopicSupport matcher = new DefaultMqttTopicSupport();

    // Verifies that '+' and '#' follow the expected MQTT wildcard matching behavior.
    @Test
    void shouldMatchWildcardFilters() {
        assertTrue(matcher.matches("sensors/+/temperature", "sensors/room-1/temperature"));
        assertTrue(matcher.matches("sensors/#", "sensors/room-1/temperature"));
        assertFalse(matcher.matches("sensors/+/temperature", "sensors/room-1/humidity"));
    }

    // Verifies that ordinary wildcard filters do not cross the MQTT system-topic '$' boundary.
    @Test
    void shouldNotMatchSystemTopicsWithPlainWildcardFilters() {
        assertFalse(matcher.matches("#", "$SYS/broker/clients"));
        assertFalse(matcher.matches("+/broker/clients", "$SYS/broker/clients"));
        assertFalse(matcher.matches("+/#", "$SYS/broker/clients"));
        assertTrue(matcher.matches("$SYS/#", "$SYS/broker/clients"));
        assertTrue(matcher.matches("$SYS/+/clients", "$SYS/broker/clients"));
    }

    // Verifies that invalid filters and invalid topic names are rejected by validation.
    @Test
    void shouldRejectInvalidFiltersAndTopicNames() {
        assertFalse(matcher.isValidFilter("sensors/#/temperature"));
        assertFalse(matcher.isValidFilter("$share/group/sensors/#"));
        assertFalse(matcher.isValidTopicName("sensors/+/temperature"));
        assertTrue(matcher.isValidTopicName("sensors/room-1/temperature"));
    }
}
