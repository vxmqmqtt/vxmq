package io.github.vxmqmqtt.vxmq.protocol.model;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;

public record SubscriptionItemResult(
        String topicFilter,
        MqttQoS grantedQos,
        MqttSubAckReasonCode reasonCode) {

    public static SubscriptionItemResult granted(String topicFilter, MqttQoS grantedQos) {
        return new SubscriptionItemResult(topicFilter, grantedQos, MqttSubAckReasonCode.qosGranted(grantedQos));
    }

    public static SubscriptionItemResult rejected(String topicFilter, MqttSubAckReasonCode reasonCode) {
        return new SubscriptionItemResult(topicFilter, MqttQoS.FAILURE, reasonCode);
    }

    public boolean accepted() {
        return grantedQos != MqttQoS.FAILURE;
    }
}
