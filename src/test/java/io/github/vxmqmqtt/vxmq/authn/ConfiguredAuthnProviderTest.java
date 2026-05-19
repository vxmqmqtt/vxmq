package io.github.vxmqmqtt.vxmq.authn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.vxmqmqtt.vxmq.protocol.model.Mqtt5ConnectRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfiguredAuthnProviderTest {

    @Test
    void shouldPermitAllWhenNoAuthnAuthenticatorsAreConfigured() {
        ConfiguredAuthnProvider provider = new ConfiguredAuthnProvider(config(
                Optional.of(AuthnNoMatchPolicy.ALLOW),
                List.of()));

        AuthnResult result = provider.authenticate(connection(), request("anonymous", null, false));

        assertEquals(AuthnResultStatus.ALLOW, result.status());
        assertEquals(AuthnReason.SUCCESS, result.reason());
    }

    @Test
    void shouldPermitAllWhenNoAuthnAuthenticatorsAndNoNoMatchPolicyAreConfigured() {
        ConfiguredAuthnProvider provider = new ConfiguredAuthnProvider(config(
                Optional.empty(),
                List.of()));

        AuthnResult result = provider.authenticate(connection(), request("anonymous", null, false));

        assertEquals(AuthnResultStatus.ALLOW, result.status());
        assertEquals(AuthnReason.SUCCESS, result.reason());
    }

    @Test
    void shouldAuthenticateConfiguredStaticPasswordUser() {
        ConfiguredAuthnProvider provider = new ConfiguredAuthnProvider(config(
                Optional.of(AuthnNoMatchPolicy.DENY),
                List.of(authenticator(
                        "local-users",
                        true,
                        "password",
                        "static",
                        List.of(user("device-a", "secret-a"))))));

        AuthnResult allowed = provider.authenticate(connection(), request("device-a", "secret-a", true));
        AuthnResult denied = provider.authenticate(connection(), request("device-a", "wrong", true));

        assertEquals(AuthnResultStatus.ALLOW, allowed.status());
        assertEquals(AuthnReason.SUCCESS, allowed.reason());
        assertEquals("device-a", allowed.principal());
        assertEquals(AuthnResultStatus.DENY, denied.status());
        assertEquals(AuthnReason.BAD_USERNAME_OR_PASSWORD, denied.reason());
    }

    @Test
    void shouldFailClosedForUnknownStaticPasswordUserWhenNoNoMatchPolicyIsConfigured() {
        ConfiguredAuthnProvider provider = new ConfiguredAuthnProvider(config(
                Optional.empty(),
                List.of(authenticator(
                        "local-users",
                        true,
                        "password",
                        "static",
                        List.of(user("device-a", "secret-a"))))));

        AuthnResult allowed = provider.authenticate(connection(), request("device-a", "secret-a", true));
        AuthnResult denied = provider.authenticate(connection(), request("unknown", "secret-a", true));

        assertEquals(AuthnResultStatus.ALLOW, allowed.status());
        assertEquals(AuthnReason.SUCCESS, allowed.reason());
        assertEquals(AuthnResultStatus.DENY, denied.status());
        assertEquals(AuthnReason.NO_MATCH, denied.reason());
    }

    @Test
    void shouldFailFastForUnsupportedConfiguredAuthnAuthenticator() {
        AuthnRuntimeConfig.AuthnAuthenticatorConfig authenticator = authenticator(
                "http-users",
                true,
                "password",
                "http",
                List.of());

        assertThrows(IllegalArgumentException.class, () -> new ConfiguredAuthnProvider(config(
                Optional.of(AuthnNoMatchPolicy.DENY),
                List.of(authenticator))));
    }

    @Test
    void shouldFailFastForDuplicateStaticUsernames() {
        AuthnRuntimeConfig.AuthnAuthenticatorConfig authenticator = authenticator(
                "local-users",
                true,
                "password",
                "static",
                List.of(
                        user("device-a", "secret-a"),
                        user("device-a", "secret-b")));

        assertThrows(IllegalArgumentException.class, () -> new ConfiguredAuthnProvider(config(
                Optional.of(AuthnNoMatchPolicy.DENY),
                List.of(authenticator))));
    }

    private ClientConnection connection() {
        return new ClientConnection("connection-1", "remote", "client-a", "MQTT", 5, true);
    }

    private Mqtt5ConnectRequest request(String username, String password, boolean passwordPresent) {
        return new Mqtt5ConnectRequest(
                "client-a",
                "MQTT",
                true,
                0L,
                username,
                password,
                passwordPresent,
                null);
    }

    private AuthnRuntimeConfig config(
            Optional<AuthnNoMatchPolicy> noMatch,
            List<AuthnRuntimeConfig.AuthnAuthenticatorConfig> authenticators) {
        return new AuthnRuntimeConfig() {
            @Override
            public Optional<AuthnNoMatchPolicy> noMatch() {
                return noMatch;
            }

            @Override
            public List<AuthnRuntimeConfig.AuthnAuthenticatorConfig> authenticators() {
                return authenticators;
            }
        };
    }

    private AuthnRuntimeConfig.AuthnAuthenticatorConfig authenticator(
            String id,
            boolean enabled,
            String mechanism,
            String backend,
            List<AuthnRuntimeConfig.UserConfig> users) {
        return new AuthnRuntimeConfig.AuthnAuthenticatorConfig() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public String mechanism() {
                return mechanism;
            }

            @Override
            public String backend() {
                return backend;
            }

            @Override
            public List<AuthnRuntimeConfig.UserConfig> users() {
                return users;
            }
        };
    }

    private AuthnRuntimeConfig.UserConfig user(String username, String password) {
        return new AuthnRuntimeConfig.UserConfig() {
            @Override
            public String username() {
                return username;
            }

            @Override
            public String password() {
                return password;
            }
        };
    }
}
