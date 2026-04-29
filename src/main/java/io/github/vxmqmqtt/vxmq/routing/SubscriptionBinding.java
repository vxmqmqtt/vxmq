package io.github.vxmqmqtt.vxmq.routing;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption.RetainedHandlingPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One stored subscription entry owned by a specific client.
 */
public record SubscriptionBinding(
        String clientId,
        String topicFilter,
        MqttQoS grantedQos,
        boolean noLocal,
        boolean retainAsPublished,
        RetainedHandlingPolicy retainHandling,
        List<Integer> subscriptionIdentifiers) {

    public SubscriptionBinding(String clientId, String topicFilter, MqttQoS grantedQos) {
        this(clientId, topicFilter, grantedQos, false, false, RetainedHandlingPolicy.SEND_AT_SUBSCRIBE, List.of());
    }

    public SubscriptionBinding(
            String clientId,
            String topicFilter,
            MqttQoS grantedQos,
            boolean noLocal,
            boolean retainAsPublished,
            RetainedHandlingPolicy retainHandling,
            Integer subscriptionIdentifier) {
        this(
                clientId,
                topicFilter,
                grantedQos,
                noLocal,
                retainAsPublished,
                retainHandling,
                subscriptionIdentifier == null ? List.of() : List.of(subscriptionIdentifier));
    }

    public SubscriptionBinding {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(topicFilter, "topicFilter");
        Objects.requireNonNull(grantedQos, "grantedQos");
        Objects.requireNonNull(retainHandling, "retainHandling");
        subscriptionIdentifiers = List.copyOf(subscriptionIdentifiers);
    }

    public SubscriptionBinding mergeForLiveDelivery(SubscriptionBinding other) {
        if (!clientId.equals(other.clientId())) {
            throw new IllegalArgumentException("Cannot merge subscriptions for different clients");
        }
        MqttQoS mergedQos = grantedQos.value() >= other.grantedQos().value() ? grantedQos : other.grantedQos();
        List<Integer> identifiers = new ArrayList<>(subscriptionIdentifiers);
        for (Integer identifier : other.subscriptionIdentifiers()) {
            if (!identifiers.contains(identifier)) {
                identifiers.add(identifier);
            }
        }
        return new SubscriptionBinding(
                clientId,
                topicFilter,
                mergedQos,
                noLocal && other.noLocal(),
                retainAsPublished || other.retainAsPublished(),
                retainHandling,
                identifiers);
    }
}
