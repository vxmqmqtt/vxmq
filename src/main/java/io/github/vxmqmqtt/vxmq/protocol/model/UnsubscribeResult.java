package io.github.vxmqmqtt.vxmq.protocol.model;

import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;
import java.util.List;

public record UnsubscribeResult(List<UnsubscribeItemResult> itemResults) {

    public List<MqttUnsubAckReasonCode> reasonCodes() {
        return itemResults.stream()
                .map(UnsubscribeItemResult::reasonCode)
                .toList();
    }
}
