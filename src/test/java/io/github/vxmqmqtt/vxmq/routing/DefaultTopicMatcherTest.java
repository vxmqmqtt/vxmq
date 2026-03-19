package io.github.vxmqmqtt.vxmq.routing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifies the minimal topic matching rules used by the in-memory router.
 */
class DefaultTopicMatcherTest {

    private final DefaultTopicMatcher matcher = new DefaultTopicMatcher();

    @Test
    void shouldMatchWildcardFilters() {
        assertTrue(matcher.matches("sensors/+/temperature", "sensors/room-1/temperature"));
        assertTrue(matcher.matches("sensors/#", "sensors/room-1/temperature"));
        assertFalse(matcher.matches("sensors/+/temperature", "sensors/room-1/humidity"));
    }

    @Test
    void shouldRejectInvalidFiltersAndTopicNames() {
        assertFalse(matcher.isValidFilter("sensors/#/temperature"));
        assertFalse(matcher.isValidFilter("$share/group/sensors/#"));
        assertFalse(matcher.isValidTopicName("sensors/+/temperature"));
        assertTrue(matcher.isValidTopicName("sensors/room-1/temperature"));
    }
}
