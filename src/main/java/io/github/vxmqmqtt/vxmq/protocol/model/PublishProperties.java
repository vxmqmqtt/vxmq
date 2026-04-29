package io.github.vxmqmqtt.vxmq.protocol.model;

import java.util.List;

/**
 * MQTT 5 PUBLISH properties currently supported by the broker protocol model.
 */
public record PublishProperties(List<PublishUserProperty> userProperties) {

    private static final PublishProperties EMPTY = new PublishProperties(List.of());

    public PublishProperties {
        userProperties = List.copyOf(userProperties);
    }

    public static PublishProperties empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return userProperties.isEmpty();
    }
}
