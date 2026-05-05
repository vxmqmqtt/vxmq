package io.github.vxmqmqtt.vxmq.authn;

/**
 * Runtime authentication resource entry.
 */
public record AuthnDefinition(
        String id,
        boolean enabled,
        AuthnAuthenticator authenticator) {
}
