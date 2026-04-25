package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * Replay one outbound PUBLISH after session resume.
 */
public record ReplayPublish(PublishDelivery delivery) implements SessionResumeAction {
}
