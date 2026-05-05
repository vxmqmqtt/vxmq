package io.github.vxmqmqtt.vxmq.authz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * CDI authorization provider backed by configured authorizer resources.
 *
 * Current milestones only support the empty configuration, which resolves to
 * permit-all. Non-empty authorizer resources fail fast until concrete authz
 * backends are implemented.
 */
@ApplicationScoped
public class ConfiguredAuthzProvider implements AuthzProvider {

    private final AuthzChain chain;

    @Inject
    public ConfiguredAuthzProvider(AuthzRuntimeConfig config) {
        this(buildChain(config));
    }

    public ConfiguredAuthzProvider(AuthzChain chain) {
        this.chain = chain;
    }

    @Override
    public AuthzResult authorize(AuthzContext context) {
        return chain.authorize(context);
    }

    private static AuthzChain buildChain(AuthzRuntimeConfig config) {
        if (config.authorizers().isEmpty()) {
            return AuthzChain.permitAll();
        }
        List<AuthzDefinition> definitions = config.authorizers().stream()
                .map(ConfiguredAuthzProvider::toDefinition)
                .toList();
        // Once configured resources exist, missing no-match policy fails closed.
        AuthzNoMatchPolicy noMatch = config.noMatch().orElse(AuthzNoMatchPolicy.DENY);
        return new AuthzChain(definitions, noMatch);
    }

    private static AuthzDefinition toDefinition(AuthzRuntimeConfig.AuthzAuthorizerConfig config) {
        throw new IllegalArgumentException(
                "Unsupported authorizer mechanism/backend: " + config.mechanism() + "/" + config.backend());
    }
}
