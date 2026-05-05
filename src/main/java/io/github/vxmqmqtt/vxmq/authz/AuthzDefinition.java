package io.github.vxmqmqtt.vxmq.authz;

/**
 * Runtime authorization resource entry.
 */
public record AuthzDefinition(
        String id,
        boolean enabled,
        AuthzAuthorizer authorizer) {
}
