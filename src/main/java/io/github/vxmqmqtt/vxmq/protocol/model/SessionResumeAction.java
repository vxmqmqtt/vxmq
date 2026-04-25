package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * One transport action that must be replayed after a subscriber session resumes.
 */
public sealed interface SessionResumeAction permits ReplayPublish, ReplayPubRel {
}
