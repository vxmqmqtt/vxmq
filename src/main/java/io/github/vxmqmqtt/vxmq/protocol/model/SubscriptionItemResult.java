package io.github.vxmqmqtt.vxmq.protocol.model;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;

/**
 * Per-filter outcome of SUBSCRIBE processing.
 */
public record SubscriptionItemResult(
        String topicFilter,
        MqttQoS grantedQos,
        MqttSubAckReasonCode reasonCode) {

    /**
     * Builds a successful subscription result for the filter.
     */
    public static SubscriptionItemResult granted(String topicFilter, MqttQoS grantedQos) {
        return new SubscriptionItemResult(topicFilter, grantedQos, MqttSubAckReasonCode.qosGranted(grantedQos));
    }

    /**
     * Builds a rejected subscription result for the filter.
     */
    public static SubscriptionItemResult rejected(String topicFilter, MqttSubAckReasonCode reasonCode) {
        return new SubscriptionItemResult(topicFilter, MqttQoS.FAILURE, reasonCode);
    }

    /**
     * Returns whether the broker accepted this filter.
     */
    public boolean accepted() {
        return grantedQos != MqttQoS.FAILURE;
    }
}
