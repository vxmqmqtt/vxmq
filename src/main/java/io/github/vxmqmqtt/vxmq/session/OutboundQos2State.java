package io.github.vxmqmqtt.vxmq.session;

/**
 * Broker-side state for an outbound QoS 2 delivery to a subscriber.
 */
public enum OutboundQos2State {
    PUBLISH_SENT,
    PUBREL_SENT
}
