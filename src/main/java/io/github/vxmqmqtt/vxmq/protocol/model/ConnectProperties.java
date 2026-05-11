package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * MQTT 5 CONNECT properties currently supported by the broker protocol model.
 */
public record ConnectProperties(MqttUserProperties userProperties, int receiveMaximum, int maximumPacketSize) {

    public static final int DEFAULT_RECEIVE_MAXIMUM = 65_535;
    public static final int DEFAULT_MAXIMUM_PACKET_SIZE = 268_435_455;
    private static final ConnectProperties EMPTY = new ConnectProperties(
            MqttUserProperties.empty(),
            DEFAULT_RECEIVE_MAXIMUM,
            DEFAULT_MAXIMUM_PACKET_SIZE);

    public ConnectProperties(MqttUserProperties userProperties) {
        this(userProperties, DEFAULT_RECEIVE_MAXIMUM, DEFAULT_MAXIMUM_PACKET_SIZE);
    }

    public ConnectProperties(MqttUserProperties userProperties, int receiveMaximum) {
        this(userProperties, receiveMaximum, DEFAULT_MAXIMUM_PACKET_SIZE);
    }

    public ConnectProperties {
        userProperties = userProperties == null ? MqttUserProperties.empty() : userProperties;
        if (receiveMaximum < 1 || receiveMaximum > DEFAULT_RECEIVE_MAXIMUM) {
            throw new IllegalArgumentException("receiveMaximum must be between 1 and 65535");
        }
        if (maximumPacketSize < 1 || maximumPacketSize > DEFAULT_MAXIMUM_PACKET_SIZE) {
            throw new IllegalArgumentException("maximumPacketSize must be between 1 and 268435455");
        }
    }

    public static ConnectProperties empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return userProperties.isEmpty()
                && receiveMaximum == DEFAULT_RECEIVE_MAXIMUM
                && maximumPacketSize == DEFAULT_MAXIMUM_PACKET_SIZE;
    }
}
