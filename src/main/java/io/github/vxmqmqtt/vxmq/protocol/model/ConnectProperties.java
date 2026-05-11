package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * MQTT 5 CONNECT properties currently supported by the broker protocol model.
 */
public record ConnectProperties(MqttUserProperties userProperties, int receiveMaximum) {

    public static final int DEFAULT_RECEIVE_MAXIMUM = 65_535;
    private static final ConnectProperties EMPTY = new ConnectProperties(
            MqttUserProperties.empty(),
            DEFAULT_RECEIVE_MAXIMUM);

    public ConnectProperties(MqttUserProperties userProperties) {
        this(userProperties, DEFAULT_RECEIVE_MAXIMUM);
    }

    public ConnectProperties {
        userProperties = userProperties == null ? MqttUserProperties.empty() : userProperties;
        if (receiveMaximum < 1 || receiveMaximum > DEFAULT_RECEIVE_MAXIMUM) {
            throw new IllegalArgumentException("receiveMaximum must be between 1 and 65535");
        }
    }

    public static ConnectProperties empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return userProperties.isEmpty();
    }
}
