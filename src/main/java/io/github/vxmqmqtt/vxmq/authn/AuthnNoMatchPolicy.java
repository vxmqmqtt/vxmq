package io.github.vxmqmqtt.vxmq.authn;

/**
 * Authn fallback when no authenticator reaches a final decision.
 */
public enum AuthnNoMatchPolicy {
    ALLOW,
    DENY;

    public boolean allow() {
        return this == ALLOW;
    }
}
