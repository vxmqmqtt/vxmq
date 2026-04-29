package io.github.vxmqmqtt.vxmq.protocol.model;

import io.netty.handler.codec.mqtt.MqttSubscriptionOption.RetainedHandlingPolicy;
import java.util.Objects;

/**
 * One topic filter entry extracted from a SUBSCRIBE packet.
 */
public record SubscriptionItem(
        String topicFilter,
        int requestedQos,
        boolean noLocal,
        boolean retainAsPublished,
        RetainedHandlingPolicy retainHandling,
        Integer subscriptionIdentifier) {

    public SubscriptionItem(String topicFilter, int requestedQos) {
        this(topicFilter, requestedQos, false, false, RetainedHandlingPolicy.SEND_AT_SUBSCRIBE, null);
    }

    public SubscriptionItem {
        Objects.requireNonNull(retainHandling, "retainHandling");
    }
}
