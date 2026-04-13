package io.github.vxmqmqtt.vxmq.protocol.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for explicit PUBLISH accept/reject result modeling.
 */
class PublishResultTest {

    // Verifies that accepted publishes never request connection closure.
    @Test
    void shouldBuildAcceptedPublishResultWithoutClosingConnection() {
        PublishResult result = PublishResult.accepted(List.of(), 0, true, MqttPubAckReasonCode.SUCCESS);

        assertTrue(result.accepted());
        assertFalse(result.closeConnection());
    }

    // Verifies that rejected publishes can explicitly avoid disconnecting the client.
    @Test
    void shouldBuildRejectedPublishResultWithoutDisconnect() {
        PublishResult result = PublishResult.rejectedWithoutDisconnect();

        assertFalse(result.accepted());
        assertFalse(result.closeConnection());
        assertNull(result.disconnectReasonCode());
    }

    // Verifies that rejected publishes can explicitly request connection closure with a reason.
    @Test
    void shouldBuildRejectedPublishResultWithDisconnect() {
        PublishResult result = PublishResult.rejectedWithDisconnect(MqttDisconnectReasonCode.QOS_NOT_SUPPORTED);

        assertFalse(result.accepted());
        assertTrue(result.closeConnection());
        assertTrue(result.deliveries().isEmpty());
        assertFalse(result.publishAcknowledge());
    }
}
