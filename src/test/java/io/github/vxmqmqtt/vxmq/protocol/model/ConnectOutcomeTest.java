package io.github.vxmqmqtt.vxmq.protocol.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.jupiter.api.Test;

class ConnectOutcomeTest {

    @Test
    void shouldModelAcceptedResponseAndNoTakeover() {
        ConnectOutcome outcome = ConnectOutcome.accepted(
                new AcceptedConnectResponse(false, "client-a", MqttProperties.NO_PROPERTIES),
                ConnectionTakeoverPlan.none());

        AcceptedConnectResponse response = assertInstanceOf(AcceptedConnectResponse.class, outcome.response());
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, response.returnCode());
        assertEquals("client-a", response.effectiveClientId());
        assertFalse(response.sessionPresent());
        assertFalse(outcome.takeoverPlan().requiresTakeover());
    }

    @Test
    void shouldModelRejectedResponseAndTakeoverPlan() {
        ConnectOutcome outcome = ConnectOutcome.rejected(
                new RejectedConnectResponse(
                        MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED,
                        MqttProperties.NO_PROPERTIES));

        RejectedConnectResponse response = assertInstanceOf(RejectedConnectResponse.class, outcome.response());
        assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, response.returnCode());
        assertFalse(outcome.takeoverPlan().requiresTakeover());

        ConnectionTakeoverPlan takeoverPlan = ConnectionTakeoverPlan.takeOver("old-connection");
        assertTrue(takeoverPlan.requiresTakeover());
        assertEquals("old-connection", takeoverPlan.supersededConnectionId());
    }
}
