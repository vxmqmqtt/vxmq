package io.github.vxmqmqtt.vxmq.protocol.model;

import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRecReasonCode;
import io.vertx.mqtt.messages.codes.MqttReasonCode;

/**
 * MQTT acknowledgement that the transport should send for one inbound publish.
 */
public record PublishAcknowledgement(
        PublishAcknowledgementType type,
        MqttReasonCode mqtt5ReasonCode) {

    public static PublishAcknowledgement none() {
        return new PublishAcknowledgement(PublishAcknowledgementType.NONE, null);
    }

    public static PublishAcknowledgement pubAck(MqttPubAckReasonCode reasonCode) {
        return new PublishAcknowledgement(PublishAcknowledgementType.PUBACK, reasonCode);
    }

    public static PublishAcknowledgement pubRec(MqttPubRecReasonCode reasonCode) {
        return new PublishAcknowledgement(PublishAcknowledgementType.PUBREC, reasonCode);
    }
}
