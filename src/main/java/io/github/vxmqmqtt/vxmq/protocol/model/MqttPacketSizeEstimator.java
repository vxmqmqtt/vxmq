package io.github.vxmqmqtt.vxmq.protocol.model;

import java.nio.charset.StandardCharsets;

/**
 * Estimates MQTT packet sizes for the packet/property subset currently modeled by the broker.
 */
public final class MqttPacketSizeEstimator {

    private MqttPacketSizeEstimator() {
    }

    public static int publishPacketSize(String topicName, int payloadSize, int qos, PublishProperties properties) {
        int variableHeaderSize = utf8Size(topicName);
        if (qos > 0) {
            variableHeaderSize += 2;
        }
        int propertiesSize = publishPropertiesSize(properties);
        variableHeaderSize += variableByteIntegerSize(propertiesSize) + propertiesSize;
        int remainingLength = variableHeaderSize + Math.max(payloadSize, 0);
        return 1 + variableByteIntegerSize(remainingLength) + remainingLength;
    }

    public static int publishPacketSize(PublishDelivery delivery) {
        int propertiesSize = publishPropertiesSize(delivery.properties())
                + subscriptionIdentifiersPropertiesSize(delivery.subscriptionIdentifiers());
        return publishPacketSize(
                delivery.topicName(),
                delivery.payload() == null ? 0 : delivery.payload().length,
                delivery.grantedQos().value(),
                propertiesSize);
    }

    public static int publishPacketSize(String topicName, int payloadSize, int qos, int propertiesSize) {
        int variableHeaderSize = utf8Size(topicName);
        if (qos > 0) {
            variableHeaderSize += 2;
        }
        variableHeaderSize += variableByteIntegerSize(propertiesSize) + propertiesSize;
        int remainingLength = variableHeaderSize + Math.max(payloadSize, 0);
        return 1 + variableByteIntegerSize(remainingLength) + remainingLength;
    }

    public static int publishPropertiesSize(PublishProperties properties) {
        if (properties == null) {
            return 0;
        }
        int size = 0;
        for (MqttUserProperty userProperty : properties.userProperties().values()) {
            size += 1 + utf8Size(userProperty.key()) + utf8Size(userProperty.value());
        }
        if (!properties.messageExpiry().isEmpty()) {
            size += 1 + 4;
        }
        if (properties.responseTopic() != null) {
            size += 1 + utf8Size(properties.responseTopic());
        }
        if (properties.correlationData() != null) {
            size += 1 + binarySize(properties.correlationData());
        }
        return size;
    }

    public static int subscriptionIdentifiersPropertiesSize(Iterable<Integer> subscriptionIdentifiers) {
        int size = 0;
        for (Integer subscriptionIdentifier : subscriptionIdentifiers) {
            if (subscriptionIdentifier != null) {
                size += 1 + variableByteIntegerSize(subscriptionIdentifier);
            }
        }
        return size;
    }

    public static int variableByteIntegerSize(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must not be negative");
        }
        if (value < 128) {
            return 1;
        }
        if (value < 16_384) {
            return 2;
        }
        if (value < 2_097_152) {
            return 3;
        }
        return 4;
    }

    private static int utf8Size(String value) {
        return 2 + (value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length);
    }

    private static int binarySize(byte[] value) {
        return 2 + (value == null ? 0 : value.length);
    }
}
