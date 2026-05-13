package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * MQTT 5 SUBSCRIBE properties currently supported by the broker protocol model.
 */
public record SubscriptionProperties(
        MqttUserProperties userProperties,
        Integer subscriptionIdentifier) {

    private static final SubscriptionProperties EMPTY = new SubscriptionProperties(MqttUserProperties.empty(), null);

    public SubscriptionProperties(MqttUserProperties userProperties) {
        this(userProperties, null);
    }

    public SubscriptionProperties {
        userProperties = userProperties == null ? MqttUserProperties.empty() : userProperties;
    }

    public static SubscriptionProperties empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return userProperties.isEmpty() && subscriptionIdentifier == null;
    }
}
