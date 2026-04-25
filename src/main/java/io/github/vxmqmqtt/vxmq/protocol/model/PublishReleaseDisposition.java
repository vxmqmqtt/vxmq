package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * Whether the transport should send PUBREL after processing PUBREC.
 */
public enum PublishReleaseDisposition {
    SEND,
    SKIP
}
