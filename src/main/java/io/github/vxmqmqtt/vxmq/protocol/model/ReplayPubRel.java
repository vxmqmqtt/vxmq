package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * Replay one PUBREL after session resume.
 */
public record ReplayPubRel(int packetId) implements SessionResumeAction {
}
