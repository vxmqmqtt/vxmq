package io.github.vxmqmqtt.vxmq.protocol.model;

import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;

/**
 * Per-filter outcome of UNSUBSCRIBE processing.
 */
public record UnsubscribeItemResult(
        String topicFilter,
        MqttUnsubAckReasonCode reasonCode) {

    /**
     * Builds a successful unsubscribe result for the filter.
     */
    public static UnsubscribeItemResult success(String topicFilter) {
        return new UnsubscribeItemResult(topicFilter, MqttUnsubAckReasonCode.SUCCESS);
    }

    /**
     * Builds a result for valid filters that were not registered at the broker.
     */
    public static UnsubscribeItemResult noSubscriptionExisted(String topicFilter) {
        return new UnsubscribeItemResult(topicFilter, MqttUnsubAckReasonCode.NO_SUBSCRIPTION_EXISTED);
    }

    /**
     * Builds a rejected unsubscribe result for the filter.
     */
    public static UnsubscribeItemResult rejected(String topicFilter, MqttUnsubAckReasonCode reasonCode) {
        return new UnsubscribeItemResult(topicFilter, reasonCode);
    }

    /**
     * Returns whether the broker accepted the unsubscribe item.
     */
    public boolean accepted() {
        return reasonCode == MqttUnsubAckReasonCode.SUCCESS
                || reasonCode == MqttUnsubAckReasonCode.NO_SUBSCRIPTION_EXISTED;
    }
}
