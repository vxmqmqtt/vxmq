package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * MQTT 5 UNSUBSCRIBE properties currently supported by the broker protocol model.
 */
public record UnsubscribeProperties(MqttUserProperties userProperties) {

    private static final UnsubscribeProperties EMPTY = new UnsubscribeProperties(MqttUserProperties.empty());

    public UnsubscribeProperties {
        userProperties = userProperties == null ? MqttUserProperties.empty() : userProperties;
    }

    public static UnsubscribeProperties empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return userProperties.isEmpty();
    }
}
