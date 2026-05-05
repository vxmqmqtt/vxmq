package io.github.vxmqmqtt.vxmq.authz;

/**
 * Authz provider used when no concrete authorization resources are configured.
 */
public class PermitAllAuthzProvider implements AuthzProvider {

    @Override
    public AuthzResult authorize(AuthzContext context) {
        return AuthzResult.allow();
    }
}
