package io.github.vxmqmqtt.vxmq.authz;

import java.util.List;

/**
 * Ordered authorization chain for MQTT client operations.
 */
public class AuthzChain {

    private final List<AuthzDefinition> authorizers;
    private final AuthzNoMatchPolicy noMatchPolicy;

    public AuthzChain(
            List<AuthzDefinition> authorizers,
            AuthzNoMatchPolicy noMatchPolicy) {
        this.authorizers = List.copyOf(authorizers);
        this.noMatchPolicy = noMatchPolicy == null ? configuredChainDefaultNoMatchPolicy() : noMatchPolicy;
    }

    public static AuthzChain permitAll() {
        return new AuthzChain(
                List.of(new AuthzDefinition("permit-all", true, new PermitAllAuthzAuthorizer())),
                AuthzNoMatchPolicy.ALLOW);
    }

    public AuthzResult authorize(AuthzContext context) {
        for (AuthzDefinition definition : authorizers) {
            if (!definition.enabled()) {
                continue;
            }
            AuthzResult result;
            try {
                result = definition.authorizer().authorize(context);
            } catch (RuntimeException exception) {
                return AuthzResult.deny(
                        AuthzReason.BACKEND_ERROR,
                        backendFailureMessage("Authorizer", definition.id(), exception));
            }
            if (result.status() == AuthzResultStatus.ALLOW) {
                return result;
            }
            if (result.status() == AuthzResultStatus.DENY) {
                return result;
            }
        }
        return noMatchPolicy.allow()
                ? AuthzResult.allow()
                : AuthzResult.deny(AuthzReason.NO_MATCH);
    }

    private static String backendFailureMessage(String resourceType, String resourceId, RuntimeException exception) {
        String exceptionName = exception.getClass().getSimpleName();
        String exceptionMessage = exception.getMessage();
        if (exceptionMessage == null || exceptionMessage.isBlank()) {
            return resourceType + " backend failed: " + resourceId + " (" + exceptionName + ")";
        }
        return resourceType + " backend failed: " + resourceId + " (" + exceptionName + ": " + exceptionMessage + ")";
    }

    private static AuthzNoMatchPolicy configuredChainDefaultNoMatchPolicy() {
        return AuthzNoMatchPolicy.DENY;
    }
}
