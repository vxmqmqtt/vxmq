package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * MQTT 5 PUBLISH properties currently supported by the broker protocol model.
 */
public record PublishProperties(MqttUserProperties userProperties, MessageExpiry messageExpiry) {

    private static final PublishProperties EMPTY =
            new PublishProperties(MqttUserProperties.empty(), MessageExpiry.none());

    public PublishProperties(MqttUserProperties userProperties) {
        this(userProperties, MessageExpiry.none());
    }

    public PublishProperties {
        userProperties = userProperties == null ? MqttUserProperties.empty() : userProperties;
        messageExpiry = messageExpiry == null ? MessageExpiry.none() : messageExpiry;
    }

    public static PublishProperties empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return userProperties.isEmpty() && messageExpiry.isEmpty();
    }
}
