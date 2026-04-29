package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * MQTT 5 CONNECT properties currently supported by the broker protocol model.
 */
public record ConnectProperties(MqttUserProperties userProperties) {

    private static final ConnectProperties EMPTY = new ConnectProperties(MqttUserProperties.empty());

    public ConnectProperties {
        userProperties = userProperties == null ? MqttUserProperties.empty() : userProperties;
    }

    public static ConnectProperties empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return userProperties.isEmpty();
    }
}
