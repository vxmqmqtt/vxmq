package io.github.vxmqmqtt.vxmq.protocol.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for protocol-specific CONNECT request modeling.
 */
class ConnectRequestTest {

    // Verifies that MQTT 3.1.1 CONNECT requests expose cleanSession and leave cleanStart absent.
    @Test
    void shouldModelMqtt311FlagsWithNullCleanStart() {
        ConnectRequest request = ConnectRequest.mqtt311("client-311", "MQTT", true, null, false, null);

        assertTrue(request.isMqtt311());
        assertFalse(request.isMqtt5());
        assertEquals(Boolean.TRUE, request.cleanSession());
        assertNull(request.cleanStart());
        assertTrue(request.mqtt311CleanSession());
        assertTrue(request.startsFreshSession());
        assertFalse(request.retainsSessionOnDisconnect());
    }

    // Verifies that MQTT 5 CONNECT requests expose cleanStart and leave cleanSession absent.
    @Test
    void shouldModelMqtt5FlagsWithNullCleanSession() {
        ConnectRequest request = ConnectRequest.mqtt5("client-5", "MQTT", false, 60L, null, false, null);

        assertFalse(request.isMqtt311());
        assertTrue(request.isMqtt5());
        assertNull(request.cleanSession());
        assertEquals(Boolean.FALSE, request.cleanStart());
        assertFalse(request.mqtt5CleanStart());
        assertFalse(request.startsFreshSession());
        assertTrue(request.retainsSessionOnDisconnect());
        assertEquals(60L, request.mqtt5SessionExpiryIntervalSeconds());
    }

    // Verifies that self-contradictory MQTT 3.1.1 requests are rejected at construction time.
    @Test
    void shouldRejectMqtt311RequestWithCleanStart() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new ConnectRequest(
                "client-311",
                "MQTT",
                4,
                true,
                false,
                null,
                null,
                false,
                null));

        assertTrue(error.getMessage().contains("must not include cleanStart"));
    }

    // Verifies that self-contradictory MQTT 5 requests are rejected at construction time.
    @Test
    void shouldRejectMqtt5RequestWithCleanSession() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new ConnectRequest(
                "client-5",
                "MQTT",
                5,
                false,
                true,
                0L,
                null,
                false,
                null));

        assertTrue(error.getMessage().contains("must not include cleanSession"));
    }

    // Verifies that CONNECT requests can carry an optional will payload without losing protocol-specific flags.
    @Test
    void shouldModelOptionalWillMessage() {
        WillMessage willMessage = new WillMessage(
                "status/client-5",
                "offline".getBytes(),
                MqttQoS.AT_LEAST_ONCE,
                true);

        ConnectRequest request = ConnectRequest.mqtt5("client-5", "MQTT", false, 60L, null, false, willMessage);

        assertEquals(willMessage, request.willMessage());
        assertFalse(request.startsFreshSession());
        assertTrue(request.retainsSessionOnDisconnect());
    }
}
