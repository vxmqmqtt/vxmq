package io.github.vxmqmqtt.vxmq.authn;

import java.util.Objects;

/**
 * Rich authentication result returned by authenticators and providers.
 */
public record AuthnResult(
        AuthnResultStatus status,
        AuthnReason reason,
        String principal,
        String message) {

    public AuthnResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
    }

    public static AuthnResult allow(String principal) {
        return new AuthnResult(AuthnResultStatus.ALLOW, AuthnReason.SUCCESS, principal, null);
    }

    public static AuthnResult deny(AuthnReason reason) {
        return deny(reason, null);
    }

    public static AuthnResult deny(AuthnReason reason, String message) {
        return new AuthnResult(AuthnResultStatus.DENY, reason, null, message);
    }

    public static AuthnResult abstain() {
        return new AuthnResult(AuthnResultStatus.ABSTAIN, AuthnReason.NO_MATCH, null, null);
    }

    public boolean allowed() {
        return status == AuthnResultStatus.ALLOW;
    }
}
