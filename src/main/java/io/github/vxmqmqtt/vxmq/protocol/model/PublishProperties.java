package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * MQTT 5 PUBLISH properties currently supported by the broker protocol model.
 */
public record PublishProperties(
        MqttUserProperties userProperties,
        MessageExpiry messageExpiry,
        String responseTopic,
        byte[] correlationData,
        boolean duplicateResponseTopic,
        boolean duplicateCorrelationData) {

    private static final PublishProperties EMPTY =
            new PublishProperties(MqttUserProperties.empty(), MessageExpiry.none(), null, null, false, false);

    public PublishProperties(MqttUserProperties userProperties) {
        this(userProperties, MessageExpiry.none(), null, null, false, false);
    }

    public PublishProperties {
        userProperties = userProperties == null ? MqttUserProperties.empty() : userProperties;
        messageExpiry = messageExpiry == null ? MessageExpiry.none() : messageExpiry;
        correlationData = correlationData == null ? null : correlationData.clone();
    }

    public PublishProperties(MqttUserProperties userProperties, MessageExpiry messageExpiry) {
        this(userProperties, messageExpiry, null, null, false, false);
    }

    public PublishProperties(
            MqttUserProperties userProperties,
            MessageExpiry messageExpiry,
            String responseTopic,
            byte[] correlationData) {
        this(userProperties, messageExpiry, responseTopic, correlationData, false, false);
    }

    public static PublishProperties empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return userProperties.isEmpty()
                && messageExpiry.isEmpty()
                && responseTopic == null
                && correlationData == null
                && !duplicateResponseTopic
                && !duplicateCorrelationData;
    }

    @Override
    public byte[] correlationData() {
        return correlationData == null ? null : correlationData.clone();
    }
}
