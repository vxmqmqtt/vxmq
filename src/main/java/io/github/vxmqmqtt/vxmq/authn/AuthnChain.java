package io.github.vxmqmqtt.vxmq.authn;

import java.util.List;

/**
 * Ordered authentication chain for MQTT CONNECT attempts.
 */
public class AuthnChain {

    private final List<AuthnDefinition> authenticators;
    private final AuthnNoMatchPolicy noMatchPolicy;

    public AuthnChain(
            List<AuthnDefinition> authenticators,
            AuthnNoMatchPolicy noMatchPolicy) {
        this.authenticators = List.copyOf(authenticators);
        this.noMatchPolicy = noMatchPolicy == null ? configuredChainDefaultNoMatchPolicy() : noMatchPolicy;
    }

    public static AuthnChain permitAll() {
        return new AuthnChain(
                List.of(new AuthnDefinition("permit-all", true, new PermitAllAuthnAuthenticator())),
                AuthnNoMatchPolicy.ALLOW);
    }

    public AuthnResult authenticate(AuthnContext context) {
        for (AuthnDefinition definition : authenticators) {
            if (!definition.enabled()) {
                continue;
            }
            AuthnResult result;
            try {
                result = definition.authenticator().authenticate(context);
            } catch (RuntimeException exception) {
                return AuthnResult.deny(
                        AuthnReason.BACKEND_ERROR,
                        backendFailureMessage("Authenticator", definition.id(), exception));
            }
            if (result.status() == AuthnResultStatus.ALLOW) {
                return result;
            }
            if (result.status() == AuthnResultStatus.DENY) {
                return result;
            }
        }
        return noMatchPolicy.allow()
                ? AuthnResult.allow(null)
                : AuthnResult.deny(AuthnReason.NO_MATCH);
    }

    private static String backendFailureMessage(String resourceType, String resourceId, RuntimeException exception) {
        String exceptionName = exception.getClass().getSimpleName();
        String exceptionMessage = exception.getMessage();
        if (exceptionMessage == null || exceptionMessage.isBlank()) {
            return resourceType + " backend failed: " + resourceId + " (" + exceptionName + ")";
        }
        return resourceType + " backend failed: " + resourceId + " (" + exceptionName + ": " + exceptionMessage + ")";
    }

    private static AuthnNoMatchPolicy configuredChainDefaultNoMatchPolicy() {
        return AuthnNoMatchPolicy.DENY;
    }
}
