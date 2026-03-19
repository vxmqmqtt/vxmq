package io.github.vxmqmqtt.vxmq.protocol.model;

import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;

public record UnsubscribeItemResult(
        String topicFilter,
        MqttUnsubAckReasonCode reasonCode) {

    public static UnsubscribeItemResult success(String topicFilter) {
        return new UnsubscribeItemResult(topicFilter, MqttUnsubAckReasonCode.SUCCESS);
    }

    public static UnsubscribeItemResult noSubscriptionExisted(String topicFilter) {
        return new UnsubscribeItemResult(topicFilter, MqttUnsubAckReasonCode.NO_SUBSCRIPTION_EXISTED);
    }

    public static UnsubscribeItemResult rejected(String topicFilter, MqttUnsubAckReasonCode reasonCode) {
        return new UnsubscribeItemResult(topicFilter, reasonCode);
    }

    public boolean accepted() {
        return reasonCode == MqttUnsubAckReasonCode.SUCCESS
                || reasonCode == MqttUnsubAckReasonCode.NO_SUBSCRIPTION_EXISTED;
    }
}
