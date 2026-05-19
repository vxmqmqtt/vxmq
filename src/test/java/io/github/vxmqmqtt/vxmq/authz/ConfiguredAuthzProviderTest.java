package io.github.vxmqmqtt.vxmq.authz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfiguredAuthzProviderTest {

    @Test
    void shouldPermitAllWhenNoAuthzAuthorizersAreConfigured() {
        ConfiguredAuthzProvider provider = new ConfiguredAuthzProvider(config(
                Optional.of(AuthzNoMatchPolicy.ALLOW),
                List.of()));

        AuthzResult result = provider.authorize(context());

        assertEquals(AuthzResultStatus.ALLOW, result.status());
        assertEquals(AuthzReason.SUCCESS, result.reason());
    }

    @Test
    void shouldPermitAllWhenNoAuthzAuthorizersAndNoNoMatchPolicyAreConfigured() {
        ConfiguredAuthzProvider provider = new ConfiguredAuthzProvider(config(
                Optional.empty(),
                List.of()));

        AuthzResult result = provider.authorize(context());

        assertEquals(AuthzResultStatus.ALLOW, result.status());
        assertEquals(AuthzReason.SUCCESS, result.reason());
    }

    @Test
    void shouldFailFastForUnsupportedConfiguredAuthzAuthorizer() {
        AuthzRuntimeConfig.AuthzAuthorizerConfig authorizer = authorizer(
                "acl-1",
                true,
                "acl",
                "file");

        assertThrows(IllegalArgumentException.class, () -> new ConfiguredAuthzProvider(config(
                Optional.of(AuthzNoMatchPolicy.DENY),
                List.of(authorizer))));
    }

    private AuthzContext context() {
        ClientConnection connection = new ClientConnection("connection-1", "remote", "client-a", "MQTT", 5, true);
        connection.assignClientId("client-a");
        return new AuthzContext(
                connection,
                "client-a",
                null,
                AuthzAction.PUBLISH,
                "sensors/room-1/temperature");
    }

    private AuthzRuntimeConfig config(
            Optional<AuthzNoMatchPolicy> noMatch,
            List<AuthzRuntimeConfig.AuthzAuthorizerConfig> authorizers) {
        return new AuthzRuntimeConfig() {
            @Override
            public Optional<AuthzNoMatchPolicy> noMatch() {
                return noMatch;
            }

            @Override
            public List<AuthzRuntimeConfig.AuthzAuthorizerConfig> authorizers() {
                return authorizers;
            }
        };
    }

    private AuthzRuntimeConfig.AuthzAuthorizerConfig authorizer(
            String id,
            boolean enabled,
            String mechanism,
            String backend) {
        return new AuthzRuntimeConfig.AuthzAuthorizerConfig() {
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
        };
    }
}
