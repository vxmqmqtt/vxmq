package io.github.vxmqmqtt.vxmq.protocol.model;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import java.util.List;

/**
 * Result of processing one SUBSCRIBE packet.
 */
public record SubscribeResult(List<SubscriptionItemResult> itemResults) {

    /**
     * Converts item results to MQTT 3.1.1 style granted QoS values.
     */
    public List<MqttQoS> grantedQosLevels() {
        return itemResults.stream()
                .map(SubscriptionItemResult::grantedQos)
                .toList();
    }

    /**
     * Converts item results to MQTT 5 SUBACK reason codes.
     */
    public List<MqttSubAckReasonCode> reasonCodes() {
        return itemResults.stream()
                .map(SubscriptionItemResult::reasonCode)
                .toList();
    }
}
