package io.github.vxmqmqtt.vxmq.authz;

/**
 * Authz fallback when no authorizer reaches a final decision.
 */
public enum AuthzNoMatchPolicy {
    ALLOW,
    DENY;

    public boolean allow() {
        return this == ALLOW;
    }
}
