package io.github.vxmqmqtt.vxmq.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vxmqmqtt.vxmq.protocol.model.ConnectProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.Mqtt5ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.MqttUserProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.WillMessage;
import io.github.vxmqmqtt.vxmq.routing.DefaultMqttTopicSupport;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

class MqttProtocolValidatorTest {

    private final MqttProtocolValidator validator =
            new MqttProtocolValidator(new DefaultMqttTopicSupport(), 65_535, 268_435_455);

    // Verifies that CONNECT receive-maximum and maximum-packet-size stay in MQTT 5 legal ranges.
    @Test
    void shouldRejectInvalidMqtt5ConnectLimits() {
        assertTrue(validator.hasInvalidConnectProperties(connectWithProperties(
                new ConnectProperties(MqttUserProperties.empty(), 0, null))));
        assertTrue(validator.hasInvalidConnectProperties(connectWithProperties(
                new ConnectProperties(MqttUserProperties.empty(), null, 0))));
        assertFalse(validator.hasInvalidConnectProperties(connectWithProperties(
                new ConnectProperties(MqttUserProperties.empty(), 1, 1))));
    }

    // Verifies that invalid MQTT topic names in PUBLISH response-topic are caught by protocol validation.
    @Test
    void shouldRejectInvalidPublishResponseTopic() {
        assertTrue(validator.hasInvalidPublishProperties(
                5,
                new PublishProperties(
                        MqttUserProperties.empty(),
                        null,
                        "reply/+/invalid",
                        null)));
        assertFalse(validator.hasInvalidPublishProperties(
                4,
                new PublishProperties(
                        MqttUserProperties.empty(),
                        null,
                        "reply/+/ignored-for-mqtt3",
                        null)));
    }

    // Verifies that Will validation is scoped to MQTT 5 CONNECT and catches invalid topic metadata.
    @Test
    void shouldRejectInvalidMqtt5WillMetadata() {
        assertTrue(validator.hasInvalidWill(connectWithWill(new WillMessage(
                "status/+/offline",
                "offline".getBytes(),
                MqttQoS.AT_MOST_ONCE,
                false))));
        assertTrue(validator.hasInvalidWill(connectWithWill(new WillMessage(
                "status/client-a",
                "offline".getBytes(),
                MqttQoS.AT_MOST_ONCE,
                false,
                new PublishProperties(
                        MqttUserProperties.empty(),
                        null,
                        null,
                        null,
                        2,
                        null)))));
    }

    // Verifies SUBSCRIBE subscription identifiers must be positive when present.
    @Test
    void shouldRejectInvalidSubscriptionIdentifier() {
        assertTrue(validator.hasInvalidSubscriptionProperties(
                new SubscriptionProperties(MqttUserProperties.empty(), 0)));
        assertFalse(validator.hasInvalidSubscriptionProperties(
                new SubscriptionProperties(MqttUserProperties.empty(), 1)));
    }

    private Mqtt5ConnectRequest connectWithProperties(ConnectProperties properties) {
        return new Mqtt5ConnectRequest(
                "client-a",
                "MQTT",
                true,
                0L,
                null,
                false,
                null,
                properties);
    }

    private Mqtt5ConnectRequest connectWithWill(WillMessage willMessage) {
        return new Mqtt5ConnectRequest(
                "client-a",
                "MQTT",
                true,
                0L,
                null,
                false,
                willMessage);
    }
}
