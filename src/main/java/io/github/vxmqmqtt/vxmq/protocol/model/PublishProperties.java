package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * MQTT 5 PUBLISH properties currently supported by the broker protocol model.
 */
public record PublishProperties(MqttUserProperties userProperties) {

    private static final PublishProperties EMPTY = new PublishProperties(MqttUserProperties.empty());

    public PublishProperties {
        userProperties = userProperties == null ? MqttUserProperties.empty() : userProperties;
    }

    public static PublishProperties empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return userProperties.isEmpty();
    }
}
