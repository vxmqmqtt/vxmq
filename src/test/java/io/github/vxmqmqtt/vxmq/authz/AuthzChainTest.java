package io.github.vxmqmqtt.vxmq.authz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AuthzChainTest {

    @Test
    void shouldAllowWithPermitAllAuthz() {
        AuthzChain chain = AuthzChain.permitAll();

        AuthzResult result = chain.authorize(context(AuthzAction.PUBLISH, "sensors/room-1/temperature"));

        assertEquals(AuthzResultStatus.ALLOW, result.status());
        assertEquals(AuthzReason.SUCCESS, result.reason());
    }

    @Test
    void shouldDenyWhenAuthzAuthorizerDenies() {
        AuthzChain chain = new AuthzChain(
                List.of(new AuthzDefinition(
                        "deny",
                        true,
                        context -> AuthzResult.deny(
                                AuthzReason.NOT_AUTHORIZED,
                                "topic policy denied"))),
                AuthzNoMatchPolicy.ALLOW);

        AuthzResult result = chain.authorize(context(AuthzAction.SUBSCRIBE, "sensors/+/temperature"));

        assertEquals(AuthzResultStatus.DENY, result.status());
        assertEquals(AuthzReason.NOT_AUTHORIZED, result.reason());
        assertEquals("topic policy denied", result.message());
    }

    @Test
    void shouldUseNoMatchWhenAllAuthzAuthorizersAbstain() {
        AuthzChain chain = new AuthzChain(
                List.of(new AuthzDefinition("abstain", true, context -> AuthzResult.abstain())),
                AuthzNoMatchPolicy.DENY);

        AuthzResult result = chain.authorize(context(AuthzAction.PUBLISH, "sensors/room-1/temperature"));

        assertEquals(AuthzResultStatus.DENY, result.status());
        assertEquals(AuthzReason.NO_MATCH, result.reason());
    }

    @Test
    void shouldSkipDisabledAuthzAuthorizers() {
        AuthzChain chain = new AuthzChain(
                List.of(new AuthzDefinition(
                        "disabled-deny",
                        false,
                        context -> AuthzResult.deny(AuthzReason.NOT_AUTHORIZED))),
                AuthzNoMatchPolicy.ALLOW);

        AuthzResult result = chain.authorize(context(AuthzAction.SUBSCRIBE, "sensors/+/temperature"));

        assertEquals(AuthzResultStatus.ALLOW, result.status());
        assertEquals(AuthzReason.SUCCESS, result.reason());
    }

    @Test
    void shouldDenyWithBackendErrorWhenAuthzAuthorizerThrows() {
        AtomicBoolean laterAuthorizerCalled = new AtomicBoolean(false);
        AuthzChain chain = new AuthzChain(
                List.of(
                        new AuthzDefinition("backend", true, context -> {
                            throw new IllegalStateException("policy store unavailable");
                        }),
                        new AuthzDefinition("later", true, context -> {
                            laterAuthorizerCalled.set(true);
                            return AuthzResult.allow();
                        })),
                AuthzNoMatchPolicy.ALLOW);

        AuthzResult result = chain.authorize(context(AuthzAction.PUBLISH, "sensors/room-1/temperature"));

        assertEquals(AuthzResultStatus.DENY, result.status());
        assertEquals(AuthzReason.BACKEND_ERROR, result.reason());
        assertEquals("Authorizer backend failed: backend (IllegalStateException: policy store unavailable)", result.message());
        assertFalse(laterAuthorizerCalled.get());
    }

    @Test
    void shouldFailClosedWhenConfiguredNoMatchPolicyIsMissing() {
        AuthzChain chain = new AuthzChain(
                List.of(new AuthzDefinition("abstain", true, context -> AuthzResult.abstain())),
                null);

        AuthzResult result = chain.authorize(context(AuthzAction.SUBSCRIBE, "sensors/+/temperature"));

        assertEquals(AuthzResultStatus.DENY, result.status());
        assertEquals(AuthzReason.NO_MATCH, result.reason());
    }

    private AuthzContext context(AuthzAction action, String topic) {
        ClientConnection connection = new ClientConnection("connection-1", "remote", "client-a", "MQTT", 5, true);
        connection.assignClientId("client-a");
        connection.assignPrincipal("principal-a");
        return new AuthzContext(connection, "client-a", "principal-a", action, topic);
    }
}
