package io.github.vxmqmqtt.vxmq.authn;

import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CDI authentication provider backed by configured authenticator resources.
 */
@ApplicationScoped
public class ConfiguredAuthnProvider implements AuthnProvider {

    private final AuthnChain chain;

    @Inject
    public ConfiguredAuthnProvider(AuthnRuntimeConfig config) {
        this(buildChain(config));
    }

    public ConfiguredAuthnProvider(AuthnChain chain) {
        this.chain = chain;
    }

    @Override
    public AuthnResult authenticate(ClientConnection connection, ConnectRequest request) {
        return chain.authenticate(new AuthnContext(connection, request));
    }

    private static AuthnChain buildChain(AuthnRuntimeConfig config) {
        if (config.authenticators().isEmpty()) {
            return AuthnChain.permitAll();
        }
        List<AuthnDefinition> definitions = config.authenticators().stream()
                .map(ConfiguredAuthnProvider::toDefinition)
                .toList();
        // Once configured resources exist, missing no-match policy fails closed.
        AuthnNoMatchPolicy noMatch = config.noMatch().orElse(AuthnNoMatchPolicy.DENY);
        return new AuthnChain(definitions, noMatch);
    }

    private static AuthnDefinition toDefinition(AuthnRuntimeConfig.AuthnAuthenticatorConfig config) {
        if (!"password".equals(config.mechanism()) || !"static".equals(config.backend())) {
            throw new IllegalArgumentException(
                    "Unsupported authenticator mechanism/backend: " + config.mechanism() + "/" + config.backend());
        }
        return new AuthnDefinition(
                config.id(),
                config.enabled(),
                new StaticPasswordAuthnAuthenticator(staticUsers(config)));
    }

    private static Map<String, String> staticUsers(AuthnRuntimeConfig.AuthnAuthenticatorConfig config) {
        Map<String, String> passwordsByUsername = new LinkedHashMap<>();
        for (AuthnRuntimeConfig.UserConfig user : config.users()) {
            if (passwordsByUsername.put(user.username(), user.password()) != null) {
                throw new IllegalArgumentException("Duplicate static auth username: " + user.username());
            }
        }
        return passwordsByUsername;
    }
}
