package io.github.vxmqmqtt.vxmq.protocol.model;

import java.util.List;

/**
 * Ordered MQTT 5 User Property values as received from one packet.
 */
public record MqttUserProperties(List<MqttUserProperty> values) {

    private static final MqttUserProperties EMPTY = new MqttUserProperties(List.of());

    public MqttUserProperties {
        values = List.copyOf(values);
    }

    public static MqttUserProperties empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
