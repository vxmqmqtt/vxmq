package io.github.vxmqmqtt.vxmq.authn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.vxmqmqtt.vxmq.protocol.model.Mqtt5ConnectRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthnChainTest {

    @Test
    void shouldAllowWhenAuthnAuthenticatorAllows() {
        AuthnChain chain = new AuthnChain(
                List.of(new AuthnDefinition("allow", true, context -> AuthnResult.allow("device-a"))),
                AuthnNoMatchPolicy.DENY);

        AuthnResult result = chain.authenticate(context("device-a", "secret-a"));

        assertEquals(AuthnResultStatus.ALLOW, result.status());
        assertEquals(AuthnReason.SUCCESS, result.reason());
        assertEquals("device-a", result.principal());
    }

    @Test
    void shouldDenyWhenAuthnAuthenticatorDenies() {
        AuthnChain chain = new AuthnChain(
                List.of(new AuthnDefinition(
                        "deny",
                        true,
                        context -> AuthnResult.deny(
                                AuthnReason.BAD_USERNAME_OR_PASSWORD,
                                "password mismatch"))),
                AuthnNoMatchPolicy.ALLOW);

        AuthnResult result = chain.authenticate(context("device-a", "bad"));

        assertEquals(AuthnResultStatus.DENY, result.status());
        assertEquals(AuthnReason.BAD_USERNAME_OR_PASSWORD, result.reason());
        assertEquals("password mismatch", result.message());
    }

    @Test
    void shouldUseNoMatchWhenAllAuthnAuthenticatorsAbstain() {
        AuthnChain chain = new AuthnChain(
                List.of(new AuthnDefinition("abstain", true, context -> AuthnResult.abstain())),
                AuthnNoMatchPolicy.DENY);

        AuthnResult result = chain.authenticate(context("unknown", "secret"));

        assertEquals(AuthnResultStatus.DENY, result.status());
        assertEquals(AuthnReason.NO_MATCH, result.reason());
    }

    @Test
    void shouldFailClosedWhenConfiguredNoMatchPolicyIsMissing() {
        AuthnChain chain = new AuthnChain(
                List.of(new AuthnDefinition("abstain", true, context -> AuthnResult.abstain())),
                null);

        AuthnResult result = chain.authenticate(context("unknown", "secret"));

        assertEquals(AuthnResultStatus.DENY, result.status());
        assertEquals(AuthnReason.NO_MATCH, result.reason());
    }

    @Test
    void shouldSkipDisabledAuthnAuthenticators() {
        AuthnChain chain = new AuthnChain(
                List.of(new AuthnDefinition(
                        "disabled-deny",
                        false,
                        context -> AuthnResult.deny(AuthnReason.NOT_AUTHORIZED))),
                AuthnNoMatchPolicy.ALLOW);

        AuthnResult result = chain.authenticate(context("device-a", "secret-a"));

        assertEquals(AuthnResultStatus.ALLOW, result.status());
        assertEquals(AuthnReason.SUCCESS, result.reason());
    }

    @Test
    void shouldDenyWithBackendErrorWhenAuthnAuthenticatorThrows() {
        AtomicBoolean laterAuthenticatorCalled = new AtomicBoolean(false);
        AuthnChain chain = new AuthnChain(
                List.of(
                        new AuthnDefinition("backend", true, context -> {
                            throw new IllegalStateException("database unavailable");
                        }),
                        new AuthnDefinition("later", true, context -> {
                            laterAuthenticatorCalled.set(true);
                            return AuthnResult.allow("device-a");
                        })),
                AuthnNoMatchPolicy.ALLOW);

        AuthnResult result = chain.authenticate(context("device-a", "secret-a"));

        assertEquals(AuthnResultStatus.DENY, result.status());
        assertEquals(AuthnReason.BACKEND_ERROR, result.reason());
        assertEquals("Authenticator backend failed: backend (IllegalStateException: database unavailable)", result.message());
        assertFalse(laterAuthenticatorCalled.get());
    }

    @Test
    void shouldKeepPermitAllWhenNoAuthnAuthenticatorsAreConfigured() {
        AuthnChain chain = AuthnChain.permitAll();

        AuthnResult result = chain.authenticate(context("anonymous", null));

        assertEquals(AuthnResultStatus.ALLOW, result.status());
        assertEquals(AuthnReason.SUCCESS, result.reason());
    }

    private AuthnContext context(String username, String password) {
        ClientConnection connection = new ClientConnection("connection-1", "remote", "client-a", "MQTT", 5, true);
        return new AuthnContext(connection, new Mqtt5ConnectRequest(
                "client-a",
                "MQTT",
                true,
                0L,
                username,
                password,
                password != null,
                null));
    }
}
