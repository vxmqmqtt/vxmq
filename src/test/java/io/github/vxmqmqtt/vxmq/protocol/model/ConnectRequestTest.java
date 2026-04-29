package io.github.vxmqmqtt.vxmq.protocol.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for protocol-specific CONNECT request modeling.
 */
class ConnectRequestTest {

    @Test
    void shouldModelMqtt311ConnectRequest() {
        ConnectRequest request = new Mqtt311ConnectRequest("client-311", "MQTT", true, null, false, null);

        Mqtt311ConnectRequest mqtt311Request = assertInstanceOf(Mqtt311ConnectRequest.class, request);
        assertTrue(request.isMqtt311());
        assertFalse(request.isMqtt5());
        assertEquals("client-311", mqtt311Request.requestedClientId());
        assertTrue(mqtt311Request.cleanSession());
        assertNull(mqtt311Request.willMessage());
    }

    @Test
    void shouldModelMqtt5ConnectRequest() {
        ConnectRequest request = new Mqtt5ConnectRequest("client-5", "MQTT", false, 60L, null, false, null);

        Mqtt5ConnectRequest mqtt5Request = assertInstanceOf(Mqtt5ConnectRequest.class, request);
        assertFalse(request.isMqtt311());
        assertTrue(request.isMqtt5());
        assertFalse(mqtt5Request.cleanStart());
        assertEquals(60L, mqtt5Request.sessionExpiryIntervalSeconds());
        assertTrue(request.properties().userProperties().isEmpty());
    }

    @Test
    void shouldModelUnsupportedConnectRequest() {
        ConnectRequest request = new UnsupportedConnectRequest("client-x", "MQTT", 7, null, false, null);

        UnsupportedConnectRequest unsupportedRequest = assertInstanceOf(UnsupportedConnectRequest.class, request);
        assertFalse(request.isMqtt311());
        assertFalse(request.isMqtt5());
        assertEquals(7, unsupportedRequest.protocolVersion());
    }

    @Test
    void shouldModelOptionalWillMessage() {
        WillMessage willMessage = new WillMessage(
                "status/client-5",
                "offline".getBytes(),
                MqttQoS.AT_LEAST_ONCE,
                true);

        Mqtt5ConnectRequest request =
                new Mqtt5ConnectRequest("client-5", "MQTT", false, 60L, null, false, willMessage);

        assertEquals(willMessage, request.willMessage());
        assertFalse(request.cleanStart());
        assertEquals(60L, request.sessionExpiryIntervalSeconds());
    }

    @Test
    void shouldModelMqtt5ConnectUserProperties() {
        MqttUserProperties userProperties = new MqttUserProperties(
                java.util.List.of(new MqttUserProperty("auth-hint", "plugin-a")));

        ConnectRequest request = new Mqtt5ConnectRequest(
                "client-5",
                "MQTT",
                false,
                60L,
                null,
                false,
                null,
                userProperties);

        assertEquals(userProperties, request.properties().userProperties());
    }
}
