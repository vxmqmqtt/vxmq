package io.github.vxmqmqtt.vxmq.protocol.model;

import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;
import java.util.List;

/**
 * UNSUBACK-facing projection of unsubscribe processing.
 */
public record UnsubscribeAck(List<UnsubscribeItemResult> itemResults) {

    public List<MqttUnsubAckReasonCode> reasonCodes() {
        return itemResults.stream()
                .map(UnsubscribeItemResult::reasonCode)
                .toList();
    }
}
