package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * MQTT 5 CONNECT properties currently supported by the broker protocol model.
 */
public record ConnectProperties(MqttUserProperties userProperties, Integer receiveMaximum, Integer maximumPacketSize) {

    public static final int DEFAULT_RECEIVE_MAXIMUM = 65_535;
    public static final int DEFAULT_MAXIMUM_PACKET_SIZE = 268_435_455;
    private static final ConnectProperties EMPTY = new ConnectProperties(
            MqttUserProperties.empty(),
            null,
            null);

    public ConnectProperties(MqttUserProperties userProperties) {
        this(userProperties, null, null);
    }

    public ConnectProperties(MqttUserProperties userProperties, Integer receiveMaximum) {
        this(userProperties, receiveMaximum, null);
    }

    public ConnectProperties {
        userProperties = userProperties == null ? MqttUserProperties.empty() : userProperties;
    }

    public static ConnectProperties empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return userProperties.isEmpty()
                && receiveMaximum == null
                && maximumPacketSize == null;
    }
}
