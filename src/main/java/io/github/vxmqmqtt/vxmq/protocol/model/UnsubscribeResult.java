package io.github.vxmqmqtt.vxmq.protocol.model;

import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;
import java.util.List;

/**
 * Result of processing one UNSUBSCRIBE packet.
 */
public record UnsubscribeResult(List<UnsubscribeItemResult> itemResults) {

    /**
     * Converts item results to MQTT 5 UNSUBACK reason codes.
     */
    public List<MqttUnsubAckReasonCode> reasonCodes() {
        return itemResults.stream()
                .map(UnsubscribeItemResult::reasonCode)
                .toList();
    }
}
