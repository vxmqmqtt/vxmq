package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * Transport acknowledgement required for an accepted inbound PUBLISH.
 */
public enum PublishAcknowledgementType {
    NONE,
    PUBACK,
    PUBREC
}
