package io.github.vxmqmqtt.vxmq.authz;

import java.util.Objects;

/**
 * Rich authorization result returned by authorizers and providers.
 */
public record AuthzResult(
        AuthzResultStatus status,
        AuthzReason reason,
        String message) {

    public AuthzResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
    }

    public static AuthzResult allow() {
        return new AuthzResult(AuthzResultStatus.ALLOW, AuthzReason.SUCCESS, null);
    }

    public static AuthzResult deny(AuthzReason reason) {
        return deny(reason, null);
    }

    public static AuthzResult deny(AuthzReason reason, String message) {
        return new AuthzResult(AuthzResultStatus.DENY, reason, message);
    }

    public static AuthzResult abstain() {
        return new AuthzResult(AuthzResultStatus.ABSTAIN, AuthzReason.NO_MATCH, null);
    }

    public boolean allowed() {
        return status == AuthzResultStatus.ALLOW;
    }
}
