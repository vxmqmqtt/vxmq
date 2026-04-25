package io.github.vxmqmqtt.vxmq.protocol.model;

import java.util.List;

/**
 * Transport work that must be replayed when a session reconnects.
 */
public record SessionResumePlan(List<SessionResumeAction> actions) {

    public static SessionResumePlan empty() {
        return new SessionResumePlan(List.of());
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }
}
