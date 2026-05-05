package io.github.vxmqmqtt.vxmq.authz;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Optional;

/**
 * Configuration seed for authorization resources.
 */
@ConfigMapping(prefix = "vxmq.broker.authz")
public interface AuthzRuntimeConfig {

    Optional<AuthzNoMatchPolicy> noMatch();

    List<AuthzAuthorizerConfig> authorizers();

    interface AuthzAuthorizerConfig {

        String id();

        @WithDefault("true")
        boolean enabled();

        String mechanism();

        String backend();
    }
}
