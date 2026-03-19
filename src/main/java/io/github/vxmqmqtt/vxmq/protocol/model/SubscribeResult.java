package io.github.vxmqmqtt.vxmq.protocol.model;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import java.util.List;

public record SubscribeResult(List<SubscriptionItemResult> itemResults) {

    public List<MqttQoS> grantedQosLevels() {
        return itemResults.stream()
                .map(SubscriptionItemResult::grantedQos)
                .toList();
    }

    public List<MqttSubAckReasonCode> reasonCodes() {
        return itemResults.stream()
                .map(SubscriptionItemResult::reasonCode)
                .toList();
    }
}
