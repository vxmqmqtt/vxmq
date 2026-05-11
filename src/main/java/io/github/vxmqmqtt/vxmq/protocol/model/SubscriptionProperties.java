package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * MQTT 5 SUBSCRIBE properties currently supported by the broker protocol model.
 */
public record SubscriptionProperties(
        MqttUserProperties userProperties,
        Integer subscriptionIdentifier,
        boolean duplicateSubscriptionIdentifier) {

    private static final SubscriptionProperties EMPTY = new SubscriptionProperties(MqttUserProperties.empty(), null, false);

    public SubscriptionProperties(MqttUserProperties userProperties) {
        this(userProperties, null, false);
    }

    public SubscriptionProperties(MqttUserProperties userProperties, Integer subscriptionIdentifier) {
        this(userProperties, subscriptionIdentifier, false);
    }

    public SubscriptionProperties {
        userProperties = userProperties == null ? MqttUserProperties.empty() : userProperties;
    }

    public static SubscriptionProperties empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return userProperties.isEmpty() && subscriptionIdentifier == null && !duplicateSubscriptionIdentifier;
    }
}
