package io.github.vxmqmqtt.vxmq.protocol;

import io.github.vxmqmqtt.vxmq.protocol.model.ConnectProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.WillMessage;
import io.github.vxmqmqtt.vxmq.routing.MqttTopicSupport;

/**
 * Protocol-level validation that is independent from session and routing state mutations.
 */
final class MqttProtocolValidator {

    private final MqttTopicSupport mqttTopicSupport;
    private final int brokerReceiveMaximum;
    private final int brokerMaximumPacketSize;

    MqttProtocolValidator(
            MqttTopicSupport mqttTopicSupport,
            int brokerReceiveMaximum,
            int brokerMaximumPacketSize) {
        this.mqttTopicSupport = mqttTopicSupport;
        this.brokerReceiveMaximum = validateReceiveMaximum(brokerReceiveMaximum);
        this.brokerMaximumPacketSize = validateMaximumPacketSize(brokerMaximumPacketSize);
    }

    boolean hasInvalidConnectProperties(ConnectRequest request) {
        if (!request.isMqtt5()) {
            return false;
        }
        ConnectProperties properties = request.properties();
        return isInvalidReceiveMaximum(properties.receiveMaximum())
                || isInvalidMaximumPacketSize(properties.maximumPacketSize());
    }

    boolean hasInvalidSubscriptionProperties(SubscriptionProperties properties) {
        Integer subscriptionIdentifier = properties.subscriptionIdentifier();
        return subscriptionIdentifier != null && subscriptionIdentifier < 1;
    }

    boolean hasInvalidPublishProperties(int protocolVersion, PublishProperties properties) {
        if (protocolVersion != 5 || properties == null) {
            return false;
        }
        return properties.responseTopic() != null && !mqttTopicSupport.isValidTopicName(properties.responseTopic());
    }

    boolean hasInvalidWill(ConnectRequest request) {
        if (!request.isMqtt5() || request.willMessage() == null) {
            return false;
        }
        WillMessage willMessage = request.willMessage();
        PublishProperties properties = willMessage.properties();
        return !mqttTopicSupport.isValidTopicName(willMessage.topicName())
                || properties.responseTopic() != null && !mqttTopicSupport.isValidTopicName(properties.responseTopic())
                || isInvalidPayloadFormatIndicator(properties.payloadFormatIndicator());
    }

    boolean isPublishPacketTooLarge(int protocolVersion, int packetSize) {
        return protocolVersion == 5 && packetSize > brokerMaximumPacketSize;
    }

    int brokerReceiveMaximum() {
        return brokerReceiveMaximum;
    }

    int brokerMaximumPacketSize() {
        return brokerMaximumPacketSize;
    }

    static int validateReceiveMaximum(int receiveMaximum) {
        if (receiveMaximum < 1 || receiveMaximum > ConnectProperties.DEFAULT_RECEIVE_MAXIMUM) {
            throw new IllegalArgumentException("receiveMaximum must be between 1 and 65535");
        }
        return receiveMaximum;
    }

    static int validateMaximumPacketSize(int maximumPacketSize) {
        if (maximumPacketSize < 1 || maximumPacketSize > ConnectProperties.DEFAULT_MAXIMUM_PACKET_SIZE) {
            throw new IllegalArgumentException("maximumPacketSize must be between 1 and 268435455");
        }
        return maximumPacketSize;
    }

    private boolean isInvalidReceiveMaximum(Integer receiveMaximum) {
        return receiveMaximum != null
                && (receiveMaximum < 1 || receiveMaximum > ConnectProperties.DEFAULT_RECEIVE_MAXIMUM);
    }

    private boolean isInvalidMaximumPacketSize(Integer maximumPacketSize) {
        return maximumPacketSize != null
                && (maximumPacketSize < 1 || maximumPacketSize > ConnectProperties.DEFAULT_MAXIMUM_PACKET_SIZE);
    }

    private static boolean isInvalidPayloadFormatIndicator(Integer payloadFormatIndicator) {
        return payloadFormatIndicator != null && payloadFormatIndicator != 0 && payloadFormatIndicator != 1;
    }
}
