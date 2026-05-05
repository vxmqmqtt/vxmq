package io.github.vxmqmqtt.vxmq.authn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.vxmqmqtt.vxmq.protocol.model.Mqtt5ConnectRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StaticPasswordAuthnTest {

    @Test
    void shouldAllowMatchingUsernameAndPassword() {
        StaticPasswordAuthnAuthenticator authenticator =
                new StaticPasswordAuthnAuthenticator(Map.of("device-a", "secret-a"));

        AuthnResult result = authenticator.authenticate(context("device-a", "secret-a", true));

        assertEquals(AuthnResultStatus.ALLOW, result.status());
        assertEquals(AuthnReason.SUCCESS, result.reason());
        assertEquals("device-a", result.principal());
    }

    @Test
    void shouldDenyKnownUsernameWithWrongPassword() {
        StaticPasswordAuthnAuthenticator authenticator =
                new StaticPasswordAuthnAuthenticator(Map.of("device-a", "secret-a"));

        AuthnResult result = authenticator.authenticate(context("device-a", "wrong", true));

        assertEquals(AuthnResultStatus.DENY, result.status());
        assertEquals(AuthnReason.BAD_USERNAME_OR_PASSWORD, result.reason());
    }

    @Test
    void shouldDenyKnownUsernameWithoutPassword() {
        StaticPasswordAuthnAuthenticator authenticator =
                new StaticPasswordAuthnAuthenticator(Map.of("device-a", "secret-a"));

        AuthnResult result = authenticator.authenticate(context("device-a", null, false));

        assertEquals(AuthnResultStatus.DENY, result.status());
        assertEquals(AuthnReason.BAD_USERNAME_OR_PASSWORD, result.reason());
    }

    @Test
    void shouldAbstainForUnknownUsername() {
        StaticPasswordAuthnAuthenticator authenticator =
                new StaticPasswordAuthnAuthenticator(Map.of("device-a", "secret-a"));

        AuthnResult result = authenticator.authenticate(context("device-b", "secret-b", true));

        assertEquals(AuthnResultStatus.ABSTAIN, result.status());
        assertEquals(AuthnReason.NO_MATCH, result.reason());
    }

    private AuthnContext context(String username, String password, boolean passwordPresent) {
        ClientConnection connection = new ClientConnection("connection-1", "remote", "client-a", "MQTT", 5, true);
        return new AuthnContext(connection, new Mqtt5ConnectRequest(
                "client-a",
                "MQTT",
                true,
                0L,
                username,
                password,
                passwordPresent,
                null));
    }
}
