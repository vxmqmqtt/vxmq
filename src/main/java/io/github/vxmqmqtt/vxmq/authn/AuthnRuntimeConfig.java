package io.github.vxmqmqtt.vxmq.authn;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Optional;

/**
 * Configuration seed for authentication resources.
 */
@ConfigMapping(prefix = "vxmq.broker.authn")
public interface AuthnRuntimeConfig {

    Optional<AuthnNoMatchPolicy> noMatch();

    List<AuthnAuthenticatorConfig> authenticators();

    interface AuthnAuthenticatorConfig {

        String id();

        @WithDefault("true")
        boolean enabled();

        String mechanism();

        String backend();

        List<UserConfig> users();
    }

    interface UserConfig {

        String username();

        String password();
    }
}
