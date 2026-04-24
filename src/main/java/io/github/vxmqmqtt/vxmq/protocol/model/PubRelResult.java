package io.github.vxmqmqtt.vxmq.protocol.model;

import io.vertx.mqtt.messages.codes.MqttPubCompReasonCode;
import java.util.List;

/**
 * Result of processing an inbound PUBREL from a QoS 2 publisher.
 */
public record PubRelResult(
        List<PublishDelivery> deliveries,
        int queuedMessageCount,
        MqttPubCompReasonCode reasonCode) {

    public static PubRelResult complete(List<PublishDelivery> deliveries, int queuedMessageCount) {
        return new PubRelResult(deliveries, queuedMessageCount, MqttPubCompReasonCode.SUCCESS);
    }

    public static PubRelResult alreadyComplete() {
        return new PubRelResult(List.of(), 0, MqttPubCompReasonCode.SUCCESS);
    }
}
